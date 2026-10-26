-- V4 — Doctor reviews for search results
--
-- Enables patient reviews/ratings for doctors, used in search relevance
-- and displayed on doctor profiles.

CREATE TABLE appointment_reviews (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id       uuid NOT NULL,
    doctor_id       uuid NOT NULL,
    patient_id      uuid NOT NULL REFERENCES users (id),
    appointment_id  uuid NOT NULL REFERENCES appointments (id),
    rating          smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         text CHECK (char_length(comment) <= 1000),
    created_at      timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (clinic_id, doctor_id)
        REFERENCES doctor_profiles (clinic_id, id),

    UNIQUE (appointment_id),

    CHECK (char_length(comment) <= 1000)
);

CREATE INDEX reviews_doctor_idx ON appointment_reviews (doctor_id, rating);
CREATE INDEX reviews_patient_idx ON appointment_reviews (patient_id);
