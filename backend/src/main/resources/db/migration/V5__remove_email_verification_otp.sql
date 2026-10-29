-- V5 — remove email verification OTP purpose.
-- OTP is now only used for password reset.

-- Delete existing VERIFY_EMAIL records before constraining the column
DELETE FROM otp_codes WHERE purpose = 'VERIFY_EMAIL';

ALTER TABLE otp_codes
    DROP CONSTRAINT IF EXISTS otp_codes_purpose_check;

ALTER TABLE otp_codes
    ADD CONSTRAINT otp_codes_purpose_check
        CHECK (purpose IN ('PASSWORD_RESET'));
