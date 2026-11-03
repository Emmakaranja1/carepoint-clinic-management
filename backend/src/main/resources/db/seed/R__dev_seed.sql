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

INSERT INTO users (id, email, password_hash, full_name, phone, status, email_verified) VALUES
    -- Sunrise staff
    ('30000000-0000-4000-8000-000000000001', 'amara.njoroge@sunrise.carepoint.test',   '!dev-seed-no-login', 'Dr. Amara Njoroge',   '+254700000001', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000002', 'brian.odhiambo@sunrise.carepoint.test',  '!dev-seed-no-login', 'Dr. Brian Odhiambo',  '+254700000002', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000003', 'clara.wanjiru@sunrise.carepoint.test',   '!dev-seed-no-login', 'Dr. Clara Wanjiru',   '+254700000003', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000004', 'dennis.mutua@sunrise.carepoint.test',    '!dev-seed-no-login', 'Dennis Mutua',        '+254700000004', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000005', 'faith.akinyi@sunrise.carepoint.test',    '!dev-seed-no-login', 'Faith Akinyi',        '+254700000005', 'ACTIVE', true),
    ('30000000-0000-4000-8000-00000000000d', 'nathan.kamau@sunrise.carepoint.test',    '!dev-seed-no-login', 'Dr. Nathan Kamau',    '+254700000011', 'ACTIVE', true),
    ('30000000-0000-4000-8000-00000000000e', 'grace.owino@sunrise.carepoint.test',     '!dev-seed-no-login', 'Dr. Grace Owino',     '+254700000012', 'ACTIVE', true),
    ('30000000-0000-4000-8000-00000000000f', 'samuel.njeri@sunrise.carepoint.test',    '!dev-seed-no-login', 'Dr. Samuel Njeri',    '+254700000013', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000010', 'evans.wamaya@sunrise.carepoint.test',    '!dev-seed-no-login', 'Dr. Evans Wamaya',    '+254700000014', 'ACTIVE', true),
    -- Harbourview staff
    ('30000000-0000-4000-8000-000000000006', 'naledi.dlamini@harbour.carepoint.test',  '!dev-seed-no-login', 'Dr. Naledi Dlamini',  '+27710000006', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000007', 'pieter.botha@harbour.carepoint.test',    '!dev-seed-no-login', 'Dr. Pieter Botha',    '+27710000007', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000008', 'zanele.mkhize@harbour.carepoint.test',   '!dev-seed-no-login', 'Dr. Zanele Mkhize',   '+27710000008', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000009', 'thandi.ndlovu@harbour.carepoint.test',   '!dev-seed-no-login', 'Thandi Ndlovu',       '+27710000009', 'ACTIVE', true),
    ('30000000-0000-4000-8000-00000000000a', 'sipho.ngcobo@harbour.carepoint.test',    '!dev-seed-no-login', 'Sipho Ngcobo',        '+27710000010', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000011', 'mila.chen@harbour.carepoint.test',       '!dev-seed-no-login', 'Dr. Mila Chen',       '+27710000011', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000012', 'omar.khan@harbour.carepoint.test',      '!dev-seed-no-login', 'Dr. Omar Khan',       '+27710000012', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000013', 'aisha.majola@harbour.carepoint.test',    '!dev-seed-no-login', 'Dr. Aisha Majola',    '+27710000013', 'ACTIVE', true),
    ('30000000-0000-4000-8000-000000000014', 'leo.mokoena@harbour.carepoint.test',     '!dev-seed-no-login', 'Dr. Leo Mokoena',    '+27710000014', 'ACTIVE', true),
    -- patients
    ('30000000-0000-4000-8000-00000000000b', 'demo.patient@carepoint.test',            '!dev-seed-no-login', 'Demo Patient',        '+254700000100', 'ACTIVE', true),
    ('30000000-0000-4000-8000-00000000000c', 'walkin.guest@carepoint.test',            NULL,                 'Walk-in Guest',       NULL,            'GUEST',  false)
ON CONFLICT (email) DO NOTHING;

-- ---------- memberships ----------

INSERT INTO clinic_memberships (id, clinic_id, user_id, role) VALUES
    ('40000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000002', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000003', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000004', 'RECEPTIONIST'),
    ('40000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005', 'CLINIC_ADMIN'),
    ('40000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-00000000000d', 'DOCTOR'),
    ('40000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-00000000000e', 'DOCTOR'),
    ('40000000-0000-4000-8000-00000000000d', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-00000000000f', 'DOCTOR'),
    ('40000000-0000-4000-8000-00000000000e', '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000010', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000006', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000007', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000008', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000009', 'RECEPTIONIST'),
    ('40000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-00000000000a', 'CLINIC_ADMIN'),
    ('40000000-0000-4000-8000-00000000000f', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000011', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000010', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000012', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000011', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000013', 'DOCTOR'),
    ('40000000-0000-4000-8000-000000000012', '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000014', 'DOCTOR')
ON CONFLICT (clinic_id, user_id, role) DO NOTHING;

-- ---------- doctor profiles ----------

INSERT INTO doctor_profiles (id, clinic_id, membership_id, specialty_id, bio, consult_minutes) VALUES
    ('50000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-0000000000a1', 'Fictional GP with an interest in preventive care.', 30),
    ('50000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-0000000000a2', 'Fictional pediatrician.', 20),
    ('50000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-0000000000a5', 'Fictional dentist.', 30),
    ('50000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-00000000000b', '10000000-0000-4000-8000-0000000000a1', 'Fictional clinic GP focused on long-term care plans and preventive screening.', 30),
    ('50000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-00000000000c', '10000000-0000-4000-8000-0000000000a2', 'Fictional pediatric consultant helping families manage routine and complex childhood care.', 20),
    ('50000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-00000000000d', '10000000-0000-4000-8000-0000000000a4', 'Fictional cardiology specialist with same-day review pathways for urgent concerns.', 45),
    ('50000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-00000000000e', '10000000-0000-4000-8000-0000000000a3', 'Fictional dermatologist supporting acne, eczema, and preventive skin checks.', 15),
    ('50000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-0000000000a4', 'Fictional cardiologist.', 45),
    ('50000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-0000000000a3', 'Fictional dermatologist.', 15),
    ('50000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-0000000000a6', 'Fictional physiotherapist.', 30),
    ('50000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-00000000000f', '10000000-0000-4000-8000-0000000000a3', 'Fictional dermatology lead focused on practical skincare and treatment planning.', 20),
    ('50000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-0000000000a4', 'Fictional cardiology and wellness consults for proactive heart care.', 30),
    ('50000000-0000-4000-8000-00000000000d', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-0000000000a1', 'Fictional family doctor with a strong focus on preventive medicine and patient education.', 25),
    ('50000000-0000-4000-8000-00000000000e', '20000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000012', '10000000-0000-4000-8000-0000000000a6', 'Fictional physiotherapist helping patients recover strength, mobility, and confidence.', 30)
ON CONFLICT (membership_id) DO NOTHING;

-- ---------- schedules: Mon–Fri (day_of_week 0=Mon) ----------
-- Guarded by NOT EXISTS so re-applying the seed never duplicates windows.

INSERT INTO doctor_schedules (clinic_id, doctor_id, day_of_week, start_time, end_time, slot_minutes)
SELECT '20000000-0000-4000-8000-000000000001', d.id, dow, s.start_time, s.end_time, s.slot
FROM (VALUES
        ('50000000-0000-4000-8000-000000000001'::uuid),
        ('50000000-0000-4000-8000-000000000002'::uuid),
        ('50000000-0000-4000-8000-000000000003'::uuid),
        ('50000000-0000-4000-8000-000000000007'::uuid),
        ('50000000-0000-4000-8000-000000000008'::uuid),
        ('50000000-0000-4000-8000-000000000009'::uuid),
        ('50000000-0000-4000-8000-00000000000a'::uuid)) AS d(id)
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
        ('50000000-0000-4000-8000-000000000006'::uuid),
        ('50000000-0000-4000-8000-00000000000b'::uuid),
        ('50000000-0000-4000-8000-00000000000c'::uuid),
        ('50000000-0000-4000-8000-00000000000d'::uuid),
        ('50000000-0000-4000-8000-00000000000e'::uuid)) AS d(id)
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
     'BOOKED', '30000000-0000-4000-8000-000000000004', 'Fictional walk-in booked by reception', true),
    ('70000000-0000-4000-8000-000000000004',
     '20000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000007',
     '30000000-0000-4000-8000-00000000000b',
     tstzrange(current_date + 1 + time '10:30', current_date + 1 + time '11:00'),
     'BOOKED', '30000000-0000-4000-8000-00000000000b', 'Preventive care review', false),
    ('70000000-0000-4000-8000-000000000005',
     '20000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-00000000000b',
     '30000000-0000-4000-8000-00000000000b',
     tstzrange(current_date + 2 + time '12:00', current_date + 2 + time '12:30'),
     'BOOKED', '30000000-0000-4000-8000-00000000000b', 'Skin health consult', false),
    ('70000000-0000-4000-8000-000000000006',
     '20000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-00000000000d',
     '30000000-0000-4000-8000-00000000000b',
     tstzrange(current_date + 4 + time '11:00', current_date + 4 + time '11:45'),
     'BOOKED', '30000000-0000-4000-8000-000000000009', 'Cardiology follow-up', false)
ON CONFLICT (id) DO NOTHING;

-- ---------- media metadata (object keys only — bytes live in local object storage) ----------

INSERT INTO media_objects (clinic_id, purpose, object_key, bucket, content_type, byte_size, status, owner_type, owner_id, uploaded_by) VALUES
    ('20000000-0000-4000-8000-000000000001', 'CLINIC_LOGO',  'public/20000000-0000-4000-8000-000000000001/logo.webp',  'carepoint-public-media', 'image/webp',  42810, 'ACTIVE', 'CLINIC',         '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', 'DOCTOR_PHOTO', 'public/20000000-0000-4000-8000-000000000001/dr-amara-njoroge.webp', 'carepoint-public-media', 'image/webp', 61233, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', 'DOCTOR_PHOTO', 'public/00201ed7-8d56-4e5a-a535-c968f632fd22/46c45116-eab8-43c7-9947-d314fbb5b7de.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-000000000007', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', 'DOCTOR_PHOTO', 'public/0632e8f7-c175-4069-b3dc-08c522ff8209/55edc945-81f0-4819-b560-c160632ddec7.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-000000000008', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', 'DOCTOR_PHOTO', 'public/111513b7-ae8a-4a66-9cb4-4ddc7e76b0f9/81825f6a-1c2b-4d28-bfcd-8a4d58419498.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-000000000009', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000001', 'DOCTOR_PHOTO', 'public/17e11ad6-edc3-4135-8fc7-47823b22c9d3/c229a7e3-9ac5-4c31-9ef2-3a2b39bda698.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-00000000000a', '30000000-0000-4000-8000-000000000005'),
    ('20000000-0000-4000-8000-000000000002', 'CLINIC_LOGO',  'public/20000000-0000-4000-8000-000000000002/logo.webp',  'carepoint-public-media', 'image/webp',  39044, 'ACTIVE', 'CLINIC',         '20000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-00000000000a'),
    ('20000000-0000-4000-8000-000000000002', 'DOCTOR_PHOTO', 'public/2fd689bc-6b9a-446c-b7a6-efe5ffc3c42f/ec0e26c7-b126-49c1-ba57-76ce9a7e2387.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-00000000000b', '30000000-0000-4000-8000-00000000000a'),
    ('20000000-0000-4000-8000-000000000002', 'DOCTOR_PHOTO', 'public/33941585-91d1-44d8-8621-b6baad7d6dc2/d24b2098-e8cd-454b-bc50-745e804b0989.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-00000000000c', '30000000-0000-4000-8000-00000000000a'),
    ('20000000-0000-4000-8000-000000000002', 'DOCTOR_PHOTO', 'public/48de9ee4-b9b0-48c2-b41a-9c9af7eca96b/257f7ead-3761-45f2-a024-0a05a7a2ec0f.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-00000000000d', '30000000-0000-4000-8000-00000000000a'),
    ('20000000-0000-4000-8000-000000000002', 'DOCTOR_PHOTO', 'public/5dc8dec4-21b1-467b-ad9f-51d1165846d1/2ea47d25-98b7-4316-8c90-d16ff513451a.png', 'carepoint-public-media', 'image/png', 182100, 'ACTIVE', 'DOCTOR_PROFILE', '50000000-0000-4000-8000-00000000000e', '30000000-0000-4000-8000-00000000000a')
ON CONFLICT (object_key) DO NOTHING;
