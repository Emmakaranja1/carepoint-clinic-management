package com.carepoint.schema;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Minimal schema fixture for constraint-level tests: one clinic, one staff
 * user, one membership, one doctor profile, one patient. Inserts are plain
 * SQL — these tests exist to prove database guarantees, independent of any
 * application service code.
 */
final class SchemaFixture {

    final UUID clinicId = UUID.randomUUID();
    final UUID staffUserId = UUID.randomUUID();
    final UUID membershipId = UUID.randomUUID();
    final UUID doctorId = UUID.randomUUID();
    final UUID patientId = UUID.randomUUID();

    private SchemaFixture() {
    }

    static SchemaFixture create(JdbcTemplate jdbc) {
        SchemaFixture f = new SchemaFixture();
        jdbc.update(
                "INSERT INTO clinics (id, name, slug, timezone) VALUES (?, ?, ?, 'Africa/Nairobi')",
                f.clinicId, "Test Clinic", "test-clinic-" + f.clinicId);
        jdbc.update(
                "INSERT INTO specialties (id, name) VALUES (gen_random_uuid(), ?)",
                "Test Specialty " + f.clinicId);
        jdbc.update(
                "INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, '!test', 'Staff User')",
                f.staffUserId, "staff-" + f.staffUserId + "@test.local");
        jdbc.update(
                "INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, '!test', 'Patient User')",
                f.patientId, "patient-" + f.patientId + "@test.local");
        jdbc.update(
                "INSERT INTO clinic_memberships (id, clinic_id, user_id, role) VALUES (?, ?, ?, 'DOCTOR')",
                f.membershipId, f.clinicId, f.staffUserId);
        jdbc.update(
                "INSERT INTO doctor_profiles (id, clinic_id, membership_id, specialty_id) "
                        + "SELECT ?, ?, ?, id FROM specialties WHERE name = ?",
                f.doctorId, f.clinicId, f.membershipId, "Test Specialty " + f.clinicId);
        return f;
    }
}
