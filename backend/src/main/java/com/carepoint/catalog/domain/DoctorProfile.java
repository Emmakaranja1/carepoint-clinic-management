package com.carepoint.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "doctor_profiles")
public class DoctorProfile {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "membership_id", nullable = false, unique = true)
    private UUID membershipId;

    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;

    private String bio;

    @Column(name = "consult_minutes")
    private Integer consultMinutes;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoctorProfile() {
    }

    public DoctorProfile(UUID clinicId, UUID membershipId, UUID specialtyId,
                         String bio, Integer consultMinutes) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.membershipId = membershipId;
        this.specialtyId = specialtyId;
        this.bio = bio;
        this.consultMinutes = consultMinutes;
        this.createdAt = Instant.now();
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

    public UUID getSpecialtyId() {
        return specialtyId;
    }

    public String getBio() {
        return bio;
    }

    public Integer getConsultMinutes() {
        return consultMinutes;
    }

    public void update(UUID specialtyId, String bio, Integer consultMinutes) {
        this.specialtyId = specialtyId;
        this.bio = bio;
        this.consultMinutes = consultMinutes;
    }
}
