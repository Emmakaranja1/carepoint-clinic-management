package com.carepoint.clinic.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_invitations")
public class StaffInvitation {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StaffInvitation() {
    }

    public StaffInvitation(UUID clinicId, UUID membershipId, String tokenHash,
                           Instant expiresAt, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.membershipId = membershipId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public boolean isOpen(Instant now) {
        return consumedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getMembershipId() {
        return membershipId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
