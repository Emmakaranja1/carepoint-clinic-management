package com.carepoint.reporting.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;

/**
 * Clinic summary report (docs/01 US-P1, docs/09 §2.6). Read-only aggregates
 * over a bounded date range — five fixed queries, no per-row lookups.
 * Utilization = occupied slots ÷ scheduled capacity (schedule ∩ range −
 * time-off), computed in memory per docs/09 §5.5-style bounded math.
 */
@Service
public class ReportService {

    private static final int MAX_RANGE_DAYS = 366;

    private final ClinicAuthorization authz;
    private final ClinicRepository clinics;
    private final JdbcTemplate jdbc;

    public ReportService(ClinicAuthorization authz, ClinicRepository clinics,
                         JdbcTemplate jdbc) {
        this.authz = authz;
        this.clinics = clinics;
        this.jdbc = jdbc;
    }

    public record DoctorRow(UUID doctorId, String doctorName,
                            long booked, long cancelled, long completed,
                            long noShow, long capacity, double utilization) {
    }

    public record SummaryReport(LocalDate from, LocalDate to,
                                Map<String, Long> byStatus, long total,
                                double noShowRate, List<DoctorRow> perDoctor) {
    }

    public SummaryReport summary(CarepointPrincipal principal, UUID clinicId,
                                 LocalDate from, LocalDate to) {
        authz.requireRole(principal, clinicId, "RECEPTIONIST", "CLINIC_ADMIN");
        Clinic clinic = clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        ZoneId tz = ZoneId.of(clinic.getTimezone());
        LocalDate f = from != null ? from : LocalDate.now(tz).minusDays(30);
        LocalDate t = to != null ? to : LocalDate.now(tz);
        if (t.isBefore(f)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("to", "to must not be before from"));
        }
        if (f.plusDays(MAX_RANGE_DAYS).isBefore(t)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("to", "Range is limited to 12 months"));
        }

        Instant rangeStart = f.atStartOfDay(tz).toInstant();
        Instant rangeEnd = t.plusDays(1).atStartOfDay(tz).toInstant();
        Timestamp rStart = Timestamp.from(rangeStart);
        Timestamp rEnd = Timestamp.from(rangeEnd);

        // ---- query 1: status totals ----
        Map<String, Long> byStatus = new LinkedHashMap<>(
                Map.of("BOOKED", 0L, "CANCELLED", 0L, "COMPLETED", 0L, "NO_SHOW", 0L));
        jdbc.query("""
                SELECT status, count(*) FROM appointments
                WHERE clinic_id = ? AND slot && tstzrange(?, ?, '[)')
                GROUP BY status
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> byStatus.put(rs.getString(1), rs.getLong(2)),
                clinicId, rStart, rEnd);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long completed = byStatus.get("COMPLETED");
        long noShow = byStatus.get("NO_SHOW");
        double noShowRate = completed + noShow == 0 ? 0.0
                : (double) noShow / (completed + noShow);

        // ---- query 2: per-doctor appointment counts ----
        Map<UUID, long[]> perDoctorStatus = new HashMap<>(); // [booked, cancelled, completed, noShow]
        jdbc.query("""
                SELECT a.doctor_id, a.status, count(*)
                FROM appointments a
                WHERE a.clinic_id = ? AND a.slot && tstzrange(?, ?, '[)')
                GROUP BY a.doctor_id, a.status
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            long[] counts = perDoctorStatus.computeIfAbsent(
                    UUID.fromString(rs.getString(1)), k -> new long[4]);
            switch (rs.getString(2)) {
                case "BOOKED" -> counts[0] = rs.getLong(3);
                case "CANCELLED" -> counts[1] = rs.getLong(3);
                case "COMPLETED" -> counts[2] = rs.getLong(3);
                case "NO_SHOW" -> counts[3] = rs.getLong(3);
                default -> { }
            }
        }, clinicId, rStart, rEnd);

        // ---- query 3: doctor display names ----
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("""
                SELECT dp.id, u.full_name
                FROM doctor_profiles dp
                JOIN clinic_memberships m ON m.id = dp.membership_id
                JOIN users u ON u.id = m.user_id
                WHERE dp.clinic_id = ?
                """, (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> names.put(UUID.fromString(rs.getString(1)), rs.getString(2)),
                clinicId);

        // ---- query 4: schedules (capacity source) ----
        record Sched(UUID doctorId, int dow, java.time.LocalTime start,
                     java.time.LocalTime end, int slotMin,
                     LocalDate validFrom, LocalDate validTo) {
        }
        List<Sched> schedules = jdbc.query("""
                SELECT doctor_id, day_of_week, start_time, end_time,
                       slot_minutes, valid_from, valid_to
                FROM doctor_schedules WHERE clinic_id = ?
                """, (rs, n) -> new Sched(
                UUID.fromString(rs.getString(1)),
                rs.getInt(2),
                rs.getTime(3).toLocalTime(),
                rs.getTime(4).toLocalTime(),
                rs.getInt(5),
                rs.getDate(6).toLocalDate(),
                rs.getDate(7) == null ? null : rs.getDate(7).toLocalDate()),
                clinicId);

        // ---- query 5: time-off overlaps in range ----
        Map<UUID, List<long[]>> offByDoctor = new HashMap<>();
        jdbc.query("""
                SELECT doctor_id, off_from, off_to FROM doctor_time_off
                WHERE clinic_id = ? AND off_from < ? AND off_to > ?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> offByDoctor
                .computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new java.util.ArrayList<>())
                .add(new long[]{rs.getTimestamp(2).toInstant().toEpochMilli(),
                        rs.getTimestamp(3).toInstant().toEpochMilli()}),
                clinicId, rEnd, rStart);

        // ---- capacity math: windows ∩ range − time-off (per doctor) ----
        Map<UUID, Long> capacity = new HashMap<>();
        for (LocalDate day = f; !day.isAfter(t); day = day.plusDays(1)) {
            int dow = day.getDayOfWeek().getValue() - 1;
            for (Sched s : schedules) {
                if (s.dow() != dow || day.isBefore(s.validFrom())
                        || (s.validTo() != null && day.isAfter(s.validTo()))) {
                    continue;
                }
                List<long[]> offs = offByDoctor.getOrDefault(s.doctorId(), List.of());
                long cursor = day.atTime(s.start()).atZone(tz).toInstant().toEpochMilli();
                long windowEnd = day.atTime(s.end()).atZone(tz).toInstant().toEpochMilli();
                long step = s.slotMin() * 60_000L;
                while (cursor + step <= windowEnd) {
                    if (!overlaps(offs, cursor, cursor + step)) {
                        capacity.merge(s.doctorId(), 1L, Long::sum);
                    }
                    cursor += step;
                }
            }
        }

        List<DoctorRow> perDoctor = buildRows(names, perDoctorStatus, capacity);
        return new SummaryReport(f, t, byStatus, total, noShowRate, perDoctor);
    }

    private List<DoctorRow> buildRows(Map<UUID, String> names,
                                      Map<UUID, long[]> status,
                                      Map<UUID, Long> capacity) {
        java.util.Set<UUID> ids = new java.util.TreeSet<>(names.keySet());
        ids.addAll(status.keySet());
        ids.addAll(capacity.keySet());
        List<DoctorRow> rows = new java.util.ArrayList<>();
        for (UUID id : ids) {
            long[] c = status.getOrDefault(id, new long[4]);
            long cap = capacity.getOrDefault(id, 0L);
            long occupied = c[0] + c[2] + c[3]; // cancelled freed the slot
            rows.add(new DoctorRow(id, names.getOrDefault(id, "(removed)"),
                    c[0], c[1], c[2], c[3], cap,
                    cap == 0 ? 0.0 : (double) occupied / cap));
        }
        rows.sort(java.util.Comparator.comparing(DoctorRow::doctorName,
                java.util.Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    private boolean overlaps(List<long[]> offs, long start, long end) {
        for (long[] o : offs) {
            if (start < o[1] && end > o[0]) {
                return true;
            }
        }
        return false;
    }
}
