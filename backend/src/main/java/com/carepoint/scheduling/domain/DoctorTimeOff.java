package com.carepoint.scheduling.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor_time_off")
public class DoctorTimeOff {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "off_from", nullable = false)
    private Instant offFrom;

    @Column(name = "off_to", nullable = false)
    private Instant offTo;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoctorTimeOff() {
    }

    public DoctorTimeOff(UUID clinicId, UUID doctorId,
                         Instant offFrom, Instant offTo, String reason) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.doctorId = doctorId;
        this.offFrom = offFrom;
        this.offTo = offTo;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public Instant getOffFrom() {
        return offFrom;
    }

    public Instant getOffTo() {
        return offTo;
    }

    public String getReason() {
        return reason;
    }
}
