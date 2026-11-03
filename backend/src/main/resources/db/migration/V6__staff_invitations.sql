-- V6 — staff invitations.
--
-- Invariants enforced here, not in code:
--   * an invitation can only point at a membership of its own clinic
--   * only the SHA-256 of the token is stored; the raw token only ever
--     travels inside the invitation email
--   * a membership has at most one open (unconsumed, unrevoked) invitation

CREATE TABLE staff_invitations (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id     uuid        NOT NULL,
    membership_id uuid        NOT NULL,
    token_hash    text        NOT NULL UNIQUE,
    expires_at    timestamptz NOT NULL,
    consumed_at   timestamptz,
    revoked_at    timestamptz,
    created_by    uuid        NOT NULL REFERENCES users (id),
    created_at    timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (clinic_id, membership_id)
        REFERENCES clinic_memberships (clinic_id, id),
    CHECK (expires_at > created_at),
    CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE UNIQUE INDEX staff_invitations_open_idx
    ON staff_invitations (membership_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

-- latest-invitation-per-membership probe used by the staff directory
CREATE INDEX staff_invitations_membership_idx
    ON staff_invitations (membership_id, created_at DESC);
