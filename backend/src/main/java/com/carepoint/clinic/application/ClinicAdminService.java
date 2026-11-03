package com.carepoint.clinic.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;

/** Clinic-admin workspace reads: overview aggregates and the audit activity feed. */
@Service
public class ClinicAdminService {

    private final JdbcTemplate jdbc;
    private final ClinicRepository clinics;
    private final ClinicAuthorization authz;

    public ClinicAdminService(JdbcTemplate jdbc, ClinicRepository clinics, ClinicAuthorization authz) {
        this.jdbc = jdbc;
        this.clinics = clinics;
        this.authz = authz;
    }

    public record ClinicInfo(UUID id, String name, String slug, String timezone,
                             int cancellationCutoffHours, int slotLookaheadDays, String status) {
    }

    public record StaffSummary(long total, long active, long disabled, long pendingInvitations,
                               Map<String, Long> activeByRole) {
    }

    public record Overview(ClinicInfo clinic, StaffSummary staff) {
    }

    public record ActivityItem(long id, String action, Instant occurredAt, String actorName,
                               String targetName, boolean actorIsTarget,
                               String role, String from, String to) {
    }

    public record ActivityPage(List<ActivityItem> content, int page, int size, long totalElements) {
    }

    public Overview overview(CarepointPrincipal principal, UUID clinicId) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        Clinic clinic = clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        Map<String, Long> activeByRole = new LinkedHashMap<>(
                Map.of("CLINIC_ADMIN", 0L, "DOCTOR", 0L, "RECEPTIONIST", 0L));
        long[] totals = new long[2];
        jdbc.query("""
                SELECT role, status, count(*) FROM clinic_memberships
                WHERE clinic_id = ? GROUP BY role, status
                """, (RowCallbackHandler) rs -> {
            long n = rs.getLong(3);
            if ("ACTIVE".equals(rs.getString(2))) {
                totals[0] += n;
                activeByRole.merge(rs.getString(1), n, Long::sum);
            } else {
                totals[1] += n;
            }
        }, clinicId);
        Long pending = jdbc.queryForObject("""
                SELECT count(*) FROM staff_invitations
                WHERE clinic_id = ? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > now()
                """, Long.class, clinicId);

        return new Overview(
                new ClinicInfo(clinic.getId(), clinic.getName(), clinic.getSlug(), clinic.getTimezone(),
                        clinic.getCancellationCutoffHours(), clinic.getSlotLookaheadDays(), clinic.getStatus()),
                new StaffSummary(totals[0] + totals[1], totals[0], totals[1],
                        pending == null ? 0 : pending, activeByRole));
    }

    public ActivityPage activity(CarepointPrincipal principal, UUID clinicId, int page, int size) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), StaffDirectory.MAX_PAGE_SIZE);
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE clinic_id = ?", Long.class, clinicId);
        List<ActivityItem> rows = jdbc.query("""
                SELECT e.id, e.action, e.created_at, actor.full_name, target.full_name,
                       e.metadata->>'role', e.metadata->>'from', e.metadata->>'to',
                       e.actor_id = tm.user_id
                FROM audit_events e
                LEFT JOIN users actor ON actor.id = e.actor_id
                LEFT JOIN clinic_memberships tm ON tm.id = e.target_id AND tm.clinic_id = e.clinic_id
                LEFT JOIN users target ON target.id = tm.user_id
                WHERE e.clinic_id = ?
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT ? OFFSET ?
                """, (rs, n) -> new ActivityItem(rs.getLong(1), rs.getString(2),
                        rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getString(5),
                        rs.getBoolean(9), rs.getString(6), rs.getString(7), rs.getString(8)),
                clinicId, s, (long) p * s);
        return new ActivityPage(rows, p, s, total == null ? 0 : total);
    }
}
