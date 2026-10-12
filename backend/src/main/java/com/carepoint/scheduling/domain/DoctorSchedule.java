package com.carepoint.scheduling.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Weekly recurring availability window (clinic-local times).
 * dayOfWeek: 0=Monday … 6=Sunday, per docs/07.
 */
@Entity
@Table(name = "doctor_schedules")
public class DoctorSchedule {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false)
    private UUID clinicId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false)
    private short slotMinutes;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DoctorSchedule() {
    }

    public DoctorSchedule(UUID clinicId, UUID doctorId, int dayOfWeek,
                          LocalTime startTime, LocalTime endTime, int slotMinutes,
                          LocalDate validFrom, LocalDate validTo) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.doctorId = doctorId;
        this.dayOfWeek = (short) dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotMinutes = (short) slotMinutes;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}
