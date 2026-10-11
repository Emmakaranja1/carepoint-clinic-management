-- V2 — Core tenant schema.
--
-- Invariants enforced here, not in code:
--   * every tenant-scoped row carries clinic_id
--   * composite FKs (clinic_id, parent_id) make cross-clinic references
--     uninsertable — a row can only point at a parent in its own clinic
--   * a doctor has at most one BOOKED appointment per overlapping time range
--     (partial exclusion constraint — cancellations free the slot)
--   * GUEST users can never carry a password_hash (can never log in)

-- ---------- identity ----------

CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email         citext NOT NULL UNIQUE,
    password_hash text,
    full_name     text   NOT NULL,
    phone         text,
    status        text   NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'DISABLED', 'GUEST')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    -- GUEST rows exist so receptionists can book for unregistered patients;
    -- they must never authenticate, so they must never have a password.
    CHECK ((status = 'GUEST') = (password_hash IS NULL))
);

-- ---------- tenancy ----------

CREATE TABLE clinics (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                      text NOT NULL,
    slug                      text NOT NULL UNIQUE,
    timezone                  text NOT NULL,
    cancellation_cutoff_hours int  NOT NULL DEFAULT 24
                              CHECK (cancellation_cutoff_hours >= 0),
    slot_lookahead_days       int  NOT NULL DEFAULT 30
                              CHECK (slot_lookahead_days > 0),
    address                   jsonb,
    status                    text NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE clinic_memberships (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id  uuid NOT NULL REFERENCES clinics (id),
    user_id    uuid NOT NULL REFERENCES users (id),
    role       text NOT NULL CHECK (role IN ('DOCTOR', 'RECEPTIONIST', 'CLINIC_ADMIN')),
    status     text NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    UNIQUE (clinic_id, user_id, role),
    -- referenced by composite FKs below; proves same-clinic membership
    UNIQUE (id, clinic_id)
);

CREATE INDEX memberships_user_idx ON clinic_memberships (user_id);

CREATE TABLE specialties (
    id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL UNIQUE
);

-- ---------- doctors ----------

CREATE TABLE doctor_profiles (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       uuid NOT NULL,
    membership_id   uuid NOT NULL UNIQUE,
    specialty_id    uuid NOT NULL REFERENCES specialties (id),
    bio             text,
    consult_minutes int CHECK (consult_minutes IS NULL OR consult_minutes > 0),
    version         bigint NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (clinic_id, membership_id)
        REFERENCES clinic_memberships (clinic_id, id)
        DEFERRABLE INITIALLY DEFERRED,

    -- referenced by composite FKs below
    UNIQUE (id, clinic_id)
);

CREATE INDEX doctor_profiles_clinic_idx ON doctor_profiles (clinic_id, specialty_id);

CREATE TABLE doctor_schedules (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    uuid NOT NULL,
    doctor_id    uuid NOT NULL,
    day_of_week  smallint NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time   time NOT NULL,
    end_time     time NOT NULL CHECK (end_time > start_time),
    slot_minutes smallint NOT NULL CHECK (slot_minutes IN (10, 15, 20, 30, 45, 60)),
    valid_from   date NOT NULL DEFAULT CURRENT_DATE,
    valid_to     date CHECK (valid_to IS NULL OR valid_to > valid_from),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (clinic_id, doctor_id)
        REFERENCES doctor_profiles (clinic_id, id)
);

CREATE INDEX doctor_schedules_lookup_idx ON doctor_schedules (doctor_id, day_of_week);

CREATE TABLE doctor_time_off (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id  uuid NOT NULL,
    doctor_id  uuid NOT NULL,
    off_from   timestamptz NOT NULL,
    off_to     timestamptz NOT NULL CHECK (off_to > off_from),
    reason     text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (clinic_id, doctor_id)
        REFERENCES doctor_profiles (clinic_id, id)
);

CREATE INDEX timeoff_doctor_range_idx
    ON doctor_time_off USING gist (doctor_id, tstzrange(off_from, off_to, '[)'));

-- ---------- appointments ----------

CREATE TABLE appointments (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id        uuid NOT NULL,
    doctor_id        uuid NOT NULL,
    patient_id       uuid NOT NULL REFERENCES users (id),
    slot             tstzrange NOT NULL CHECK (NOT isempty(slot)),
    status           text NOT NULL DEFAULT 'BOOKED'
                     CHECK (status IN ('BOOKED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    reason           text CHECK (char_length(reason) <= 500),
    created_by       uuid NOT NULL REFERENCES users (id),
    booked_for_guest boolean NOT NULL DEFAULT false,
    cancelled_at     timestamptz,
    version          bigint NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (clinic_id, doctor_id)
        REFERENCES doctor_profiles (clinic_id, id)
);

-- The double-booking guarantee. Only BOOKED rows participate, so a
-- cancellation frees the slot at the storage level. Violation surfaces
-- as SQLState 23P01 → mapped to 409 SLOT_TAKEN in the service layer.
-- The implicit GiST index also serves the availability overlap probe,
-- so no separate appointments(doctor_id, slot) GiST index is needed.
ALTER TABLE appointments
    ADD CONSTRAINT no_overlapping_bookings
    EXCLUDE USING gist (doctor_id WITH =, slot WITH &&)
    WHERE (status = 'BOOKED');

CREATE INDEX appointments_patient_time_idx ON appointments (patient_id, lower(slot));
CREATE INDEX appointments_clinic_range_idx ON appointments (clinic_id, lower(slot));

-- ---------- auth support ----------

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users (id),
    family_id  uuid NOT NULL,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

CREATE TABLE idempotency_keys (
    key          uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id),
    request_hash text NOT NULL,
    response     jsonb NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- ---------- audit & media ----------

CREATE TABLE audit_events (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinic_id  uuid,
    actor_id   uuid NOT NULL,
    action     text NOT NULL,
    target_id  uuid,
    metadata   jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX audit_events_clinic_time_idx ON audit_events (clinic_id, created_at);

CREATE TABLE media_objects (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id    uuid REFERENCES clinics (id),
    purpose      text NOT NULL
                 CHECK (purpose IN ('DOCTOR_PHOTO', 'CLINIC_LOGO', 'DEMO_IMAGE', 'PRIVATE_DOC')),
    object_key   text NOT NULL UNIQUE,
    bucket       text NOT NULL,
    content_type text NOT NULL,
    byte_size    bigint NOT NULL CHECK (byte_size > 0),
    status       text NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'ACTIVE', 'ORPHANED')),
    owner_type   text,
    owner_id     uuid,
    uploaded_by  uuid NOT NULL REFERENCES users (id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX media_owner_idx ON media_objects (owner_type, owner_id);
CREATE INDEX media_pending_idx ON media_objects (status) WHERE status = 'PENDING';

-- ---------- housekeeping ----------

-- Keeps updated_at honest even for writes that bypass the app.
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_users_updated_at              BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_clinics_updated_at            BEFORE UPDATE ON clinics
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_memberships_updated_at        BEFORE UPDATE ON clinic_memberships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_doctor_profiles_updated_at    BEFORE UPDATE ON doctor_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_doctor_schedules_updated_at   BEFORE UPDATE ON doctor_schedules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_doctor_time_off_updated_at    BEFORE UPDATE ON doctor_time_off
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_appointments_updated_at       BEFORE UPDATE ON appointments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_media_objects_updated_at      BEFORE UPDATE ON media_objects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
