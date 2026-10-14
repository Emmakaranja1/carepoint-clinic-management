package com.carepoint.booking.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.booking.application.BookingService.AppointmentView;
import com.carepoint.booking.domain.Appointment;
import com.carepoint.booking.persistence.AppointmentRepository;
import com.carepoint.catalog.persistence.DoctorProfileRepository;
import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.UserRepository;

/**
 * Receptionist desk (docs/01 §5.6 US-R1..R3) and the doctor's own agenda
 * (US-S3). All endpoints are clinic-scoped; cross-clinic ids 404.
 */
@Service
public class DeskService {

    private static final Set<String> STATUSES =
            Set.of("BOOKED", "CANCELLED", "COMPLETED", "NO_SHOW");
    private static final Set<String> TARGETS =
            Set.of("CANCELLED", "COMPLETED", "NO_SHOW");

    private final ClinicAuthorization authz;
    private final BookingService booking;
    private final AppointmentRepository appointments;
    private final ClinicRepository clinics;
    private final DoctorProfileRepository profiles;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public DeskService(ClinicAuthorization authz, BookingService booking,
                       AppointmentRepository appointments, ClinicRepository clinics,
                       DoctorProfileRepository profiles, UserRepository users,
                       JdbcTemplate jdbc) {
        this.authz = authz;
        this.booking = booking;
        this.appointments = appointments;
        this.clinics = clinics;
        this.profiles = profiles;
        this.users = users;
        this.jdbc = jdbc;
    }

    public record AgendaItem(UUID id, UUID patientId, String patientName,
                             UUID doctorId, String doctorName, String status,
                             Instant start, Instant end, String reason,
                             boolean bookedForGuest, Instant cancelledAt) {
    }

    public record AgendaPage(List<AgendaItem> content, int page, int size,
                             long totalElements) {
    }

    // ---------- US-R1 — clinic agenda ----------

    public AgendaPage agenda(CarepointPrincipal principal, UUID clinicId,
                             LocalDate from, LocalDate to, UUID doctorId,
                             String status, int page, int size) {
        authz.requireRole(principal, clinicId, "RECEPTIONIST", "CLINIC_ADMIN");
        Clinic clinic = activeClinic(clinicId);
        ZoneId tz = ZoneId.of(clinic.getTimezone());
        LocalDate f = from != null ? from : LocalDate.now(tz);
        LocalDate t = to != null ? to : f;
        if (t.isBefore(f)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("to", "to must not be before from"));
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("status", "Must be one of " + STATUSES));
        }
        if (size < 1 || size > 100 || page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("size", "page >= 0 and 1 <= size <= 100"));
        }

        Timestamp rangeStart = Timestamp.from(f.atStartOfDay(tz).toInstant());
        Timestamp rangeEnd = Timestamp.from(t.plusDays(1).atStartOfDay(tz).toInstant());

        StringBuilder where = new StringBuilder("""
                FROM appointments a
                JOIN users pu ON pu.id = a.patient_id
                JOIN doctor_profiles dp ON dp.id = a.doctor_id
                JOIN clinic_memberships m ON m.id = dp.membership_id
                JOIN users du ON du.id = m.user_id
                WHERE a.clinic_id = ? AND lower(a.slot) < ? AND upper(a.slot) > ?
                """);
        List<Object> args = new ArrayList<>(List.of(clinicId, rangeEnd, rangeStart));
        if (doctorId != null) {
            where.append(" AND a.doctor_id = ?");
            args.add(doctorId);
        }
        if (status != null) {
            where.append(" AND a.status = ?");
            args.add(status);
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) " + where, Long.class, args.toArray());
        List<AgendaItem> rows = jdbc.query(
                "SELECT a.id, a.patient_id, pu.full_name, a.doctor_id, du.full_name,"
                        + " a.status, lower(a.slot), upper(a.slot), a.reason,"
                        + " a.booked_for_guest, a.cancelled_at "
                        + where + " ORDER BY lower(a.slot), a.id LIMIT ? OFFSET ?",
                (rs, n) -> mapRow(rs),
                appendPageArgs(args, page, size));
        return new AgendaPage(rows, page, size, total == null ? 0 : total);
    }

    private AgendaItem mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp cancelled = rs.getTimestamp(11);
        return new AgendaItem(
                UUID.fromString(rs.getString(1)),
                UUID.fromString(rs.getString(2)),
                rs.getString(3),
                UUID.fromString(rs.getString(4)),
                rs.getString(5),
                rs.getString(6),
                rs.getTimestamp(7).toInstant(),
                rs.getTimestamp(8).toInstant(),
                rs.getString(9),
                rs.getBoolean(10),
                cancelled == null ? null : cancelled.toInstant());
    }

    // ---------- US-S3 — doctor agenda ----------

    public List<AgendaItem> myAgenda(CarepointPrincipal principal, UUID clinicId,
                                   LocalDate date) {
        authz.requireMembership(principal, clinicId);
        Clinic clinic = activeClinic(clinicId);
        List<UUID> profileIds = jdbc.queryForList("""
                SELECT dp.id FROM doctor_profiles dp
                JOIN clinic_memberships m ON m.id = dp.membership_id
                WHERE dp.clinic_id = ? AND m.user_id = ?
                """, UUID.class, clinicId, principal.userId());
        if (profileIds.isEmpty()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "No doctor profile in this clinic");
        }
        AgendaPage day = agendaRows(clinic, profileIds.get(0), date, null, 0, 1000);
        return day.content();
    }

    // ---------- US-R2 — book on behalf ----------

    /**
     * Book on behalf — accepts the same Idempotency-Key contract as patient
     * booking so a retried/double-submitted request can't duplicate the
     * appointment. The key is scoped to the acting staff member + request.
     */
    @Transactional
    public AppointmentView bookOnBehalf(CarepointPrincipal principal, UUID clinicId,
                                        UUID doctorId, Instant start, Instant end,
                                        String reason, UUID patientId,
                                        String patientEmail, String patientName,
                                        String patientPhone, UUID idempotencyKey) {
        authz.requireRole(principal, clinicId, "RECEPTIONIST", "CLINIC_ADMIN");
        Clinic clinic = activeClinic(clinicId);
        if (profiles.findByIdAndClinicId(doctorId, clinicId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
        }
        if ((patientId == null) == (patientEmail == null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("patient", "Provide exactly one of patientId or patient details"));
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("end", "end must be after start"));
        }

        User patient = patientId != null
                ? resolveById(patientId)
                : resolveOrCreateGuest(patientEmail, patientName, patientPhone);
        UUID staffId = principal.userId();
        UUID pid = patient.getId();
        String requestHash = idempotencyKey == null ? null
                : BookingService.requestHash(clinicId, doctorId, pid, start, end, reason);
        return booking.withIdempotency(idempotencyKey, staffId, requestHash,
                () -> booking.insertValidated(clinic, doctorId, pid, staffId,
                        start, end, reason, patient.isGuest()));
    }

    // ---------- US-R3 — manage status / reschedule ----------

    @Transactional
    public AppointmentView transition(CarepointPrincipal principal, UUID clinicId,
                                      UUID id, String target) {
        authz.requireRole(principal, clinicId, "RECEPTIONIST", "CLINIC_ADMIN");
        Appointment a = deskAppointment(clinicId, id);
        if (target == null || !TARGETS.contains(target)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("status", "Must be one of " + TARGETS));
        }
        if (!"BOOKED".equals(a.getStatus())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "A " + a.getStatus().toLowerCase() + " appointment cannot become "
                            + target.toLowerCase());
        }
        if (!"CANCELLED".equals(target) && a.getSlotStart().isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "Can only mark " + target.toLowerCase() + " after the appointment starts");
        }
        switch (target) {
            case "CANCELLED" -> a.cancel();
            case "COMPLETED" -> a.markCompleted();
            case "NO_SHOW" -> a.markNoShow();
            default -> throw new ApiException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "Illegal status transition");
        }
        return AppointmentView.of(appointments.save(a));
    }

    /**
     * Cancel + rebook in one transaction — the old slot frees the moment the
     * row flips to CANCELLED, so rescheduling onto itself is a no-op win.
     */
    @Transactional
    public AppointmentView reschedule(CarepointPrincipal principal, UUID clinicId,
                                      UUID id, UUID newDoctorId,
                                      Instant start, Instant end, String reason) {
        authz.requireRole(principal, clinicId, "RECEPTIONIST", "CLINIC_ADMIN");
        Appointment a = deskAppointment(clinicId, id);
        if (!"BOOKED".equals(a.getStatus())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "Only booked appointments can be rescheduled");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("end", "end must be after start"));
        }
        UUID doctorId = newDoctorId != null ? newDoctorId : a.getDoctorId();
        Clinic clinic = activeClinic(clinicId);
        if (profiles.findByIdAndClinicId(doctorId, clinicId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
        }
        a.cancel();
        appointments.saveAndFlush(a);
        return booking.insertValidated(clinic, doctorId, a.getPatientId(),
                principal.userId(), start, end,
                reason != null ? reason : a.getReason(), a.isBookedForGuest());
    }

    // ---------- internals ----------

    private Clinic activeClinic(UUID clinicId) {
        return clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
    }

    private Appointment deskAppointment(UUID clinicId, UUID id) {
        return appointments.findByIdAndClinicId(id, clinicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
    }

    private User resolveById(UUID patientId) {
        User u = users.findById(patientId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Request validation failed", Map.of("patientId", "Unknown patient")));
        if (!u.isActive() && !u.isGuest()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("patientId", "Patient account is disabled"));
        }
        return u;
    }

    private User resolveOrCreateGuest(String email, String fullName, String phone) {
        return users.findByEmail(email)
                .map(u -> {
                    if (!u.isActive() && !u.isGuest()) {
                        throw new ApiException(ErrorCode.VALIDATION_FAILED,
                                "Request validation failed",
                                Map.of("patient.email", "Patient account is disabled"));
                    }
                    return u;
                })
                // saveAndFlush — the JDBC appointment insert runs in the same
                // transaction and needs the row visible for the FK
                .orElseGet(() -> users.saveAndFlush(User.guest(email, fullName, phone)));
    }

    /** Day view used by myAgenda — same row shape as the clinic agenda. */
    private AgendaPage agendaRows(Clinic clinic, UUID doctorId, LocalDate date,
                                  String status, int page, int size) {
        ZoneId tz = ZoneId.of(clinic.getTimezone());
        LocalDate d = date != null ? date : LocalDate.now(tz);
        Timestamp from = Timestamp.from(d.atStartOfDay(tz).toInstant());
        Timestamp to = Timestamp.from(d.plusDays(1).atStartOfDay(tz).toInstant());
        List<AgendaItem> rows = jdbc.query("""
                SELECT a.id, a.patient_id, pu.full_name, a.doctor_id, du.full_name,
                       a.status, lower(a.slot), upper(a.slot), a.reason,
                       a.booked_for_guest, a.cancelled_at
                FROM appointments a
                JOIN users pu ON pu.id = a.patient_id
                JOIN doctor_profiles dp ON dp.id = a.doctor_id
                JOIN clinic_memberships m ON m.id = dp.membership_id
                JOIN users du ON du.id = m.user_id
                WHERE a.clinic_id = ? AND a.doctor_id = ?
                  AND lower(a.slot) < ? AND upper(a.slot) > ?
                ORDER BY lower(a.slot), a.id
                """,
                (rs, n) -> mapRow(rs),
                clinic.getId(), doctorId, to, from);
        return new AgendaPage(rows, page, size, rows.size());
    }

    private static Object[] appendPageArgs(List<Object> args, int page, int size) {
        List<Object> withPage = new ArrayList<>(args);
        withPage.add(size);
        withPage.add((long) page * size);
        return withPage.toArray();
    }
}
