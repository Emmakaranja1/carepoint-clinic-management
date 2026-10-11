package com.carepoint.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Schema-level proofs for the tenancy and booking invariants that code
 * alone could not enforce.
 */
@Testcontainers
@SpringBootTest
class SchemaConstraintsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void doctorProfileCannotReferenceMembershipFromAnotherClinic() {
        SchemaFixture f = SchemaFixture.create(jdbc);
        UUID otherClinic = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO clinics (id, name, slug, timezone) VALUES (?, ?, ?, 'Africa/Nairobi')",
                otherClinic, "Other Clinic", "other-clinic-" + otherClinic);

        // membership belongs to clinic A; profile claims clinic B → the
        // composite FK must reject it even though membership_id is valid
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO doctor_profiles (id, clinic_id, membership_id, specialty_id) "
                        + "SELECT ?, ?, ?, id FROM specialties WHERE name LIKE 'Test Specialty%' LIMIT 1",
                UUID.randomUUID(), otherClinic, f.membershipId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guestUserCannotHoldPasswordAndActiveUserMust() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, status) "
                        + "VALUES ('g1@test.local', '!hash', 'G', 'GUEST')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, status) "
                        + "VALUES ('a1@test.local', NULL, 'A', 'ACTIVE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancellingBookingFreesTheSlotForRebooking() {
        SchemaFixture f = SchemaFixture.create(jdbc);
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        UUID original = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO appointments (id, clinic_id, doctor_id, patient_id, slot, created_by) "
                        + "VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?)",
                original, f.clinicId, f.doctorId, f.patientId,
                Timestamp.from(start), Timestamp.from(end), f.staffUserId);

        // overlapping insert while BOOKED must fail
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO appointments (id, clinic_id, doctor_id, patient_id, slot, created_by) "
                        + "VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?)",
                UUID.randomUUID(), f.clinicId, f.doctorId, f.patientId,
                Timestamp.from(start), Timestamp.from(end), f.staffUserId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(
                "UPDATE appointments SET status = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                original);

        // same slot is bookable again — only BOOKED rows participate in the constraint
        jdbc.update(
                "INSERT INTO appointments (id, clinic_id, doctor_id, patient_id, slot, created_by) "
                        + "VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?)",
                UUID.randomUUID(), f.clinicId, f.doctorId, f.patientId,
                Timestamp.from(start), Timestamp.from(end), f.staffUserId);

        Integer booked = jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE doctor_id = ? AND status = 'BOOKED'",
                Integer.class, f.doctorId);
        assertThat(booked).isEqualTo(1);
    }
}
