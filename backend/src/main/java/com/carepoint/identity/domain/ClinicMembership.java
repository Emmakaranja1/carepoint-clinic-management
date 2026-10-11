package com.carepoint.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_memberships")
public class ClinicMembership {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClinicMembership() {
    }

    public ClinicMembership(UUID clinicId, UUID userId, String role) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.userId = userId;
        this.role = role;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public void changeRole(String role) {
        this.role = role;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
