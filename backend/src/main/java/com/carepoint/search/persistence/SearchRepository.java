package com.carepoint.search.persistence;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.carepoint.search.domain.DoctorSearchResult;

@Repository
public class SearchRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<DoctorSearchResult> DOCTOR_ROW_MAPPER = (rs, rowNum) ->
        new DoctorSearchResult(
            rs.getObject("doctor_id", UUID.class),
            rs.getObject("clinic_id", UUID.class),
            rs.getString("clinic_name"),
            rs.getString("doctor_name"),
            rs.getString("specialty_name"),
            rs.getString("bio"),
            (Integer) rs.getObject("consult_minutes"),
            rs.getString("location"),
            rs.getDouble("rating"),
            rs.getObject("next_available_slot", LocalDate.class),
            rs.getString("photo_url")
        );

    public SearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DoctorSearchResult> searchDoctors(
        String query,
        UUID specialtyId,
        String location,
        int limit,
        long offset
    ) {
        var sql = new StringBuilder(
            """
            SELECT
                dp.id as doctor_id,
                dp.clinic_id,
                c.name as clinic_name,
                u.full_name as doctor_name,
                s.name as specialty_name,
                dp.bio,
                dp.consult_minutes,
                (c.address->>'city') as location,
                COALESCE(AVG(r.rating), 0.0) as rating,
                NULL as next_available_slot,
                m.object_key as photo_url
            FROM doctor_profiles dp
            INNER JOIN clinic_memberships cm ON dp.membership_id = cm.id
            INNER JOIN users u ON cm.user_id = u.id
            INNER JOIN clinics c ON dp.clinic_id = c.id
            INNER JOIN specialties s ON dp.specialty_id = s.id
            LEFT JOIN media_objects m ON m.owner_type = 'DOCTOR' AND m.owner_id = dp.id AND m.purpose = 'DOCTOR_PHOTO'
            LEFT JOIN appointment_reviews r ON r.doctor_id = dp.id
            WHERE c.status = 'ACTIVE'
              AND cm.status = 'ACTIVE'
            """
        );

        var params = new java.util.ArrayList<Object>();

        if (query != null && !query.isBlank()) {
            sql.append(" AND (to_tsvector('english', u.full_name || ' ' || COALESCE(dp.bio, '')) @@ plainto_tsquery(?)");
            sql.append(" OR u.full_name ILIKE ? OR s.name ILIKE ?)");
            params.add(query);
            params.add("%" + query + "%");
            params.add("%" + query + "%");
        }

        if (specialtyId != null) {
            sql.append(" AND dp.specialty_id = ?");
            params.add(specialtyId);
        }

        if (location != null && !location.isBlank()) {
            sql.append(" AND (c.address->>'city' ILIKE ? OR c.address->>'country' ILIKE ?)");
            params.add("%" + location + "%");
            params.add("%" + location + "%");
        }

        sql.append(" GROUP BY dp.id, dp.clinic_id, c.name, u.full_name, s.name, dp.bio, dp.consult_minutes, c.address, m.object_key");
        sql.append(" ORDER BY rating DESC NULLS LAST");
        sql.append(" LIMIT ? OFFSET ?");

        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), DOCTOR_ROW_MAPPER, params.toArray());
    }

    public long countDoctors(String query, UUID specialtyId, String location) {
        var sql = new StringBuilder(
            """
            SELECT COUNT(DISTINCT dp.id)
            FROM doctor_profiles dp
            INNER JOIN clinic_memberships cm ON dp.membership_id = cm.id
            INNER JOIN users u ON cm.user_id = u.id
            INNER JOIN clinics c ON dp.clinic_id = c.id
            INNER JOIN specialties s ON dp.specialty_id = s.id
            WHERE c.status = 'ACTIVE'
              AND cm.status = 'ACTIVE'
            """
        );

        var params = new java.util.ArrayList<Object>();

        if (query != null && !query.isBlank()) {
            sql.append(" AND (to_tsvector('english', u.full_name || ' ' || COALESCE(dp.bio, '')) @@ plainto_tsquery(?)");
            sql.append(" OR u.full_name ILIKE ? OR s.name ILIKE ?)");
            params.add(query);
            params.add("%" + query + "%");
            params.add("%" + query + "%");
        }

        if (specialtyId != null) {
            sql.append(" AND dp.specialty_id = ?");
            params.add(specialtyId);
        }

        if (location != null && !location.isBlank()) {
            sql.append(" AND (c.address->>'city' ILIKE ? OR c.address->>'country' ILIKE ?)");
            params.add("%" + location + "%");
            params.add("%" + location + "%");
        }

        return jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    public Optional<DoctorSearchResult> findDoctorById(UUID doctorId) {
        var sql = """
            SELECT
                dp.id as doctor_id,
                dp.clinic_id,
                c.name as clinic_name,
                u.full_name as doctor_name,
                s.name as specialty_name,
                dp.bio,
                dp.consult_minutes,
                (c.address->>'city') as location,
                COALESCE(AVG(r.rating), 0.0) as rating,
                NULL as next_available_slot,
                m.object_key as photo_url
            FROM doctor_profiles dp
            INNER JOIN clinic_memberships cm ON dp.membership_id = cm.id
            INNER JOIN users u ON cm.user_id = u.id
            INNER JOIN clinics c ON dp.clinic_id = c.id
            INNER JOIN specialties s ON dp.specialty_id = s.id
            LEFT JOIN media_objects m ON m.owner_type = 'DOCTOR' AND m.owner_id = dp.id AND m.purpose = 'DOCTOR_PHOTO'
            LEFT JOIN appointment_reviews r ON r.doctor_id = dp.id
            WHERE dp.id = ?
            GROUP BY dp.id, dp.clinic_id, c.name, u.full_name, s.name, dp.bio, dp.consult_minutes, c.address, m.object_key
            """;

        return Optional.ofNullable(
            jdbc.queryForObject(sql, DOCTOR_ROW_MAPPER, doctorId)
        );
    }
}
