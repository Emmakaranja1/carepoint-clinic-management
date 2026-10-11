package com.carepoint.clinic.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clinics")
public class Clinic {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "cancellation_cutoff_hours", nullable = false)
    private int cancellationCutoffHours;

    @Column(name = "slot_lookahead_days", nullable = false)
    private int slotLookaheadDays;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> address;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Clinic() {
    }

    public Clinic(String name, String slug, String timezone, Map<String, Object> address) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.timezone = timezone;
        this.address = address;
        this.cancellationCutoffHours = 24;
        this.slotLookaheadDays = 30;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getStatus() {
        return status;
    }
}
