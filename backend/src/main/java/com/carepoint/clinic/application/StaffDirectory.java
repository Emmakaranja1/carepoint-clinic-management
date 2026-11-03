package com.carepoint.clinic.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;

/**
 * Clinic staff reads. One join per page (memberships × users × latest
 * invitation via LATERAL) — O(page) rows, no per-row lookups.
 */
@Service
public class StaffDirectory {

    static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> ROLES = Set.of("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private static final String FROM = """
            FROM clinic_memberships m
            JOIN users u ON u.id = m.user_id
            LEFT JOIN LATERAL (
                SELECT si.expires_at, si.consumed_at
                FROM staff_invitations si
                WHERE si.membership_id = m.id AND si.revoked_at IS NULL
                ORDER BY si.created_at DESC
                LIMIT 1
            ) inv ON true
            """;

    private static final String COLUMNS = """
            SELECT m.id, m.user_id, u.email, u.full_name, u.phone, m.role, m.status,
                   m.created_at, inv.expires_at, inv.consumed_at
            """;

    private final JdbcTemplate jdbc;
    private final ClinicAuthorization authz;

    public StaffDirectory(JdbcTemplate jdbc, ClinicAuthorization authz) {
        this.jdbc = jdbc;
        this.authz = authz;
    }

    /** ACCEPTED, PENDING, EXPIRED, or NONE when the member was never invited (e.g. clinic founder). */
    public record StaffMember(UUID membershipId, UUID userId, String email, String fullName,
                              String phone, String role, String status, String invitation,
                              Instant invitationExpiresAt, Instant joinedAt) {
    }

    public record StaffPage(List<StaffMember> content, int page, int size, long totalElements) {
    }

    public StaffPage list(CarepointPrincipal principal, UUID clinicId, String role,
                          String status, String query, int page, int size) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN", "RECEPTIONIST");
        if (role != null && !ROLES.contains(role)) {
            throw invalid("role", "Must be one of " + ROLES);
        }
        if (status != null && !STATUSES.contains(status)) {
            throw invalid("status", "Must be ACTIVE or DISABLED");
        }
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        StringBuilder where = new StringBuilder(" WHERE m.clinic_id = ?");
        List<Object> args = new ArrayList<>(List.of(clinicId));
        if (role != null) {
            where.append(" AND m.role = ?");
            args.add(role);
        }
        if (status != null) {
            where.append(" AND m.status = ?");
            args.add(status);
        }
        if (query != null && !query.isBlank()) {
            where.append(" AND (u.full_name ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(query.strip()) + "%";
            args.add(like);
            args.add(like);
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM clinic_memberships m JOIN users u ON u.id = m.user_id" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((long) p * s);
        List<StaffMember> rows = jdbc.query(
                COLUMNS + FROM + where + " ORDER BY lower(u.full_name), m.id LIMIT ? OFFSET ?",
                (rs, n) -> map(rs), pageArgs.toArray());
        return new StaffPage(rows, p, s, total == null ? 0 : total);
    }

    public StaffMember get(CarepointPrincipal principal, UUID clinicId, UUID membershipId) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        return find(clinicId, membershipId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
    }

    Optional<StaffMember> find(UUID clinicId, UUID membershipId) {
        return jdbc.query(COLUMNS + FROM + " WHERE m.clinic_id = ? AND m.id = ?",
                (rs, n) -> map(rs), clinicId, membershipId).stream().findFirst();
    }

    private static StaffMember map(ResultSet rs) throws SQLException {
        Instant expires = instant(rs.getTimestamp(9));
        Instant consumed = instant(rs.getTimestamp(10));
        String invitation = expires == null ? "NONE"
                : consumed != null ? "ACCEPTED"
                : expires.isAfter(Instant.now()) ? "PENDING"
                : "EXPIRED";
        return new StaffMember(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), invitation,
                consumed == null ? expires : null,
                instant(rs.getTimestamp(8)));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                Map.of(field, message));
    }
}
