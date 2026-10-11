-- V3 — email verification + OTP codes + OAuth-ready users.
--
-- users:
--   * email_verified — OTP verification is the proof of ownership
--   * auth_provider — 'LOCAL' password users vs 'GOOGLE' (etc.) accounts;
--     OAuth users legitimately have no password
--
-- The V2 CHECK ((status='GUEST') = (password_hash IS NULL)) is replaced by
-- two simpler rules:
--   * GUEST rows never carry a password (can never log in)
--   * non-GUEST LOCAL users must have one (OAuth accounts exempt)

ALTER TABLE users
    ADD COLUMN email_verified boolean NOT NULL DEFAULT false,
    ADD COLUMN auth_provider  text    NOT NULL DEFAULT 'LOCAL';

ALTER TABLE users DROP CONSTRAINT users_check;
ALTER TABLE users
    ADD CHECK (status <> 'GUEST' OR password_hash IS NULL),
    ADD CHECK (status = 'GUEST' OR auth_provider <> 'LOCAL' OR password_hash IS NOT NULL);

-- ---------- OTP codes ----------

CREATE TABLE otp_codes (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email       citext NOT NULL,
    user_id     uuid REFERENCES users (id),
    purpose     text NOT NULL
                CHECK (purpose IN ('VERIFY_EMAIL', 'PASSWORD_RESET')),
    code_hash   text NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    attempts    smallint NOT NULL DEFAULT 0,
    send_count  smallint NOT NULL DEFAULT 1,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- latest-code lookup per (email, purpose) — the resend/verify hot path
CREATE INDEX otp_lookup_idx ON otp_codes (email, purpose, created_at DESC);
