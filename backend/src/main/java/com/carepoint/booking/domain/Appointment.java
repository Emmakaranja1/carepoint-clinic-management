package com.carepoint.booking.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Formula;

/**
 * The `slot` tstzrange column is written by a parameterized INSERT in the
 * booking path (the exclusion constraint is the guarantee) and read back
 * through these formulas. It is never mapped as an updatable column.
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Formula("lower(slot)")
    private Instant slotStart;

    @Formula("upper(slot)")
    private Instant slotEnd;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "booked_for_guest", nullable = false)
    private boolean bookedForGuest;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Appointment() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Instant getSlotStart() {
        return slotStart;
    }

    public Instant getSlotEnd() {
        return slotEnd;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void cancel() {
        this.status = "CANCELLED";
        this.cancelledAt = Instant.now();
    }
}
