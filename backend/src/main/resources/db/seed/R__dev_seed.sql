-- R__dev_seed — fictional development/demo data.
--
-- Loaded ONLY when the `seed` Spring profile is active (flyway location
-- db/seed is added by application-seed.yml). Never part of the default
-- migration set — tests and production see schema only.
--
-- All people, clinics, emails and media are fictional. Password hashes are
-- sentinel strings that can never verify — auth lands in M2; seed users are
-- placeholders, not login credentials.
--
-- Idempotent: fixed UUIDs + ON CONFLICT DO NOTHING, safe to re-apply.

-- ---------- reference data ----------

INSERT INTO specialties (id, name) VALUES
    ('10000000-0000-4000-8000-0000000000a1', 'General Practice'),
    ('10000000-0000-4000-8000-0000000000a2', 'Pediatrics'),
    ('10000000-0000-4000-8000-0000000000a3', 'Dermatology'),
    ('10000000-0000-4000-8000-0000000000a4', 'Cardiology'),
    ('10000000-0000-4000-8000-0000000000a5', 'Dentistry'),
    ('10000000-0000-4000-8000-0000000000a6', 'Physiotherapy')
ON CONFLICT (name) DO NOTHING;

-- ---------- clinics ----------

INSERT INTO clinics (id, name, slug, timezone, cancellation_cutoff_hours, slot_lookahead_days) VALUES
    ('20000000-0000-4000-8000-000000000001', 'Sunrise Family Clinic',          'sunrise-family',    'Africa/Nairobi',      24, 30),
    ('20000000-0000-4000-8000-000000000002', 'Harbourview Specialist Practice', 'harbourview-spec', 'Africa/Johannesburg', 48, 45)
ON CONFLICT (slug) DO NOTHING;

-- ---------- users (fictional) ----------

INSERT INTO users (id, email, password_hash, full_name, phone, status) VALUES
    -- Sunrise staff
    ('30000000-0000-4000-8000-000000000001', 'amara.njoroge@sunrise.carepoint.test',   '!dev-seed-no-login', 'Dr. Amara Njoroge',   '+254700000001', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000002', 'brian.odhiambo@sunrise.carepoint.test',  '!dev-seed-no-login', 'Dr. Brian Odhiambo',  '+254700000002', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000003', 'clara.wanjiru@sunrise.carepoint.test',   '!dev-seed-no-login', 'Dr. Clara Wanjiru',   '+254700000003', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000004', 'dennis.mutua@sunrise.carepoint.test',    '!dev-seed-no-login', 'Dennis Mutua',        '+254700000004', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000005', 'faith.akinyi@sunrise.carepoint.test',    '!dev-seed-no-login', 'Faith Akinyi',        '+254700000005', 'ACTIVE'),
    -- Harbourview staff
    ('30000000-0000-4000-8000-000000000006', 'naledi.dlamini@harbour.carepoint.test',  '!dev-seed-no-login', 'Dr. Naledi Dlamini',  '+27710000006', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000007', 'pieter.botha@harbour.carepoint.test',    '!dev-seed-no-login', 'Dr. Pieter Botha',    '+27710000007', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000008', 'zanele.mkhize@harbour.carepoint.test',   '!dev-seed-no-login', 'Dr. Zanele Mkhize',   '+27710000008', 'ACTIVE'),
    ('30000000-0000-4000-8000-000000000009', 'thandi.ndlovu@harbour.carepoint.test',   '!dev-seed-no-login', 'Thandi Ndlovu',       '+27710000009', 'ACTIVE'),
    ('30000000-0000-4000-8000-00000000000a', 'sipho.ngcobo@harbour.carepoint.test',    '!dev-seed-no-login', 'Sipho Ngcobo',        '+27710000010', 'ACTIVE'),
    -- patients
    ('30000000-0000-4000-8000-00000000000b', 'demo.patient@carepoint.test',            '!dev-seed-no-login', 'Demo Patient',        '+254700000100', 'ACTIVE'),
    ('30000000-0000-4000-8000-00000000000c', 'walkin.guest@carepoint.test',            NULL,                 'Walk-in Guest',       NULL,            'GUEST')
ON CONFLICT (email) DO NOTHING;

-- ---------- memberships ----------

INSERT INTO clinic_memberships (id, clinic_id, user_id, role) VALUES
    ('40000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000003', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000004', 'RECEPTIONIST'),
    ('40000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005', 'CLINIC_ADMIN'),
    ('40000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000006', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000007', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000008', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000009', 'RECEPTIONIST'),
    ('40000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-00000000000a', 'CLINIC_ADMIN')
ON CONFLICT (clinic_id, user_id, role) DO NOTHING;

-- ---------- doctor profiles ----------

INSERT INTO doctor_profiles (id, clinic_id, membership_id, specialty_id, bio, consult_minutes) VALUES
    ('50000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-0000000000a1', 'Fictional GP with an interest in preventive care.', 30),
    ('50000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-0000000000a2', 'Fictional pediatrician.', 20),
    ('50000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-0000000000a5', 'Fictional dentist.', 30),
    ('50000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-0000000000a4', 'Fictional cardiologist.', 45),
    ('50000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-0000000000a3', 'Fictional dermatologist.', 15),
    ('50000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-0000000000a6', 'Fictional physiotherapist.', 30)
ON CONFLICT (membership_id) DO NOTHING;

-- ---------- schedules: Mon–Fri (day_of_week 0=Mon) ----------
-- Guarded by NOT EXISTS so re-applying the seed never duplicates windows.

INSERT INTO doctor_schedules (clinic_id, doctor_id, day_of_week, start_time, end_time, slot_minutes)
SELECT '20000000-0000-4000-8000-000000000001', d.id, dow, s.start_time, s.end_time, s.slot
FROM (VALUES
        ('50000000-0000-4000-8000-000000000001'::uuid),
        ('50000000-0000-4000-8000-000000000002'::uuid),
        ('50000000-0000-4000-8000-000000000003'::uuid)) AS d(id)
CROSS JOIN generate_series(0, 4) AS dow
CROSS JOIN (VALUES ('08:00'::time, '12:00'::time, 30::smallint),
                   ('14:00'::time, '17:00'::time, 30::smallint)) AS s(start_time, end_time, slot)
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_schedules ds
    WHERE ds.doctor_id = d.id AND ds.day_of_week = dow AND ds.start_time = s.start_time
);

INSERT INTO doctor_schedules (clinic_id, doctor_id, day_of_week, start_time, end_time, slot_minutes)
SELECT '20000000-0000-4000-8000-000000000002', d.id, dow, '09:00'::time, '16:00'::time, 30::smallint
FROM (VALUES
        ('50000000-0000-4000-8000-000000000004'::uuid),
        ('50000000-0000-4000-8000-000000000005'::uuid),
        ('50000000-0000-4000-8000-000000000006'::uuid)) AS d(id)
CROSS JOIN generate_series(0, 4) AS dow
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_schedules ds
    WHERE ds.doctor_id = d.id AND ds.day_of_week = dow
);

-- ---------- appointments (fixed ids keep the seed re-runnable) ----------

INSERT INTO appointments (id, clinic_id, doctor_id, patient_id, slot, status, created_by, reason, booked_for_guest) VALUES
    ('70000000-0000-4000-8000-000000000001',
     '20000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
     '30000000-0000-4000-8000-00000000000b',
     tstzrange(now() - interval '7 days', now() - interval '7 days' + interval '30 minutes'),
     'COMPLETED', '30000000-0000-4000-8000-00000000000b', 'Fictional checkup', false),
    ('70000000-0000-4000-8000-000000000002',
     '20000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001',
     '30000000-0000-4000-8000-00000000000b',
     tstzrange(current_date + 2 + time '08:30', current_date + 2 + time '09:00'),
     'BOOKED', '30000000-0000-4000-8000-00000000000b', 'Fictional follow-up', false),
    ('70000000-0000-4000-8000-000000000003',
     '20000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000002',
     '30000000-0000-4000-8000-00000000000c',
     tstzrange(current_date + 3 + time '14:00', current_date + 3 + time '14:20'),
     'BOOKED', '30000000-0000-4000-8000-000000000004', 'Fictional walk-in booked by reception', true)
ON CONFLICT (id) DO NOTHING;

-- ---------- media metadata (object keys only — bytes live in R2) ----------

INSERT INTO media_objects (clinic_id, purpose, object_key, bucket, content_type, byte_size, status, owner_type, owner_id, uploaded_by) VALUES
    ('20000000-0000-4000-8000-000000000001', 'CLINIC_LOGO',  'public/20000000-0000-4000-8000-000000000001/logo.webp',  'carepoint-public-media', 'image/webp',  42810, 'ACTIVE', 'clinic',         '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', 'DOCTOR_PHOTO', 'public/20000000-0000-4000-8000-000000000001/dr-amara-njoroge.webp', 'carepoint-public-media', 'image/webp', 61233, 'ACTIVE', 'doctor_profile', '50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000002', 'CLINIC_LOGO',  'public/20000000-0000-4000-8000-000000000002/logo.webp',  'carepoint-public-media', 'image/webp',  39044, 'ACTIVE', 'clinic',         '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-00000000000a')
ON CONFLICT (object_key) DO NOTHING;
