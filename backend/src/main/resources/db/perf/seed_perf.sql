-- seed_perf.sql — large synthetic dataset for query-plan measurement.
--
-- NOT a Flyway migration and never loaded automatically. Run on demand
-- against a throwaway database only:
--
--   docker exec -i carepoint-postgres \
--     psql -U carepoint -d carepoint \
--     < backend/src/main/resources/db/perf/seed_perf.sql
--
-- Generates: 5 clinics x 50 doctors, 2 000 patients, ~90 000 appointments
-- spread over a 60-day window. All data fictional. Deterministic UUIDs are
-- derived from row numbers so the file is safe to inspect; the dataset is
-- meant to be disposable (drop/recreate the DB when done).
--
-- Runtime: a few seconds on a laptop.

BEGIN;

-- one shared specialty + 5 clinics
INSERT INTO specialties (id, name) VALUES
    ('f0000000-0000-4000-8000-000000000001', 'Perf General Practice')
ON CONFLICT (name) DO NOTHING;

INSERT INTO clinics (id, name, slug, timezone)
SELECT 'f1000000-0000-4000-8000-' || lpad(to_hex(c), 12, '0'),
       'Perf Clinic ' || c,
       'perf-clinic-' || c,
       'Africa/Nairobi'
FROM generate_series(1, 5) AS c
ON CONFLICT (slug) DO NOTHING;

-- 250 doctors (50 per clinic): user + membership + profile
INSERT INTO users (id, email, password_hash, full_name, status)
SELECT 'f2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
       'perf.doctor.' || n || '@carepoint.test',
       '!perf-seed-no-login',
       'Perf Doctor ' || n,
       'ACTIVE'
FROM generate_series(1, 250) AS n
ON CONFLICT (email) DO NOTHING;

INSERT INTO clinic_memberships (id, clinic_id, user_id, role)
SELECT 'f3000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
       'f1000000-0000-4000-8000-' || lpad(to_hex(((n - 1) / 50) + 1), 12, '0'),
       'f2000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
       'DOCTOR'
FROM generate_series(1, 250) AS n
ON CONFLICT (clinic_id, user_id, role) DO NOTHING;

INSERT INTO doctor_profiles (id, clinic_id, membership_id, specialty_id, consult_minutes)
SELECT 'f4000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
       'f1000000-0000-4000-8000-' || lpad(to_hex(((n - 1) / 50) + 1), 12, '0'),
       'f3000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
       'f0000000-0000-4000-8000-000000000001',
       30
FROM generate_series(1, 250) AS n
ON CONFLICT (membership_id) DO NOTHING;

-- 2 000 patients
INSERT INTO users (id, email, password_hash, full_name, status)
SELECT 'f5000000-0000-4000-8000-' || lpad(to_hex(n), 12, '0'),
       'perf.patient.' || n || '@carepoint.test',
       '!perf-seed-no-login',
       'Perf Patient ' || n,
       'ACTIVE'
FROM generate_series(1, 2000) AS n
ON CONFLICT (email) DO NOTHING;

-- ~90 000 appointments: 360 per doctor, non-overlapping 30-minute slots
-- spread over 60 days (6 slots/day, 08:00 start). ~10% CANCELLED to keep
-- the partial exclusion index realistically mixed.
INSERT INTO appointments (clinic_id, doctor_id, patient_id, slot, status, created_by)
SELECT 'f1000000-0000-4000-8000-' || lpad(to_hex(((d - 1) / 50) + 1), 12, '0'),
       'f4000000-0000-4000-8000-' || lpad(to_hex(d), 12, '0'),
       'f5000000-0000-4000-8000-' || lpad(to_hex(((s * 37 + d) % 2000) + 1), 12, '0'),
       tstzrange(
           (current_date - 30 + (s / 6))::timestamp + (time '08:00' + ((s % 6) * 30 || ' minutes')::interval),
           (current_date - 30 + (s / 6))::timestamp + (time '08:30' + ((s % 6) * 30 || ' minutes')::interval)),
       CASE WHEN s % 10 = 0 THEN 'CANCELLED' ELSE 'BOOKED' END,
       'f2000000-0000-4000-8000-' || lpad(to_hex(d), 12, '0')
FROM generate_series(1, 250) AS d
CROSS JOIN generate_series(1, 360) AS s;

ANALYZE appointments;
ANALYZE doctor_profiles;

COMMIT;
