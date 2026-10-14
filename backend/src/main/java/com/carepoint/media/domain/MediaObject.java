package com.carepoint.media.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_objects")
public class MediaObject {

    @Id
    private UUID id;

    @Column(name = "clinic_id")
    private UUID clinicId;

    /** DOCTOR_PHOTO | CLINIC_LOGO | DEMO_IMAGE (PRIVATE_DOC is not wired up). */
    @Column(nullable = false)
    private String purpose;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    /** PENDING → ACTIVE | ORPHANED (confirm failed / reaped). */
    @Column(nullable = false)
    private String status;

    /** DOCTOR_PROFILE | CLINIC | null for pure demo assets. */
    @Column(name = "owner_type")
    private String ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaObject() {
    }

    public MediaObject(UUID clinicId, String purpose, String objectKey,
                       String bucket, String contentType, long byteSize,
                       String ownerType, UUID ownerId, UUID uploadedBy) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.purpose = purpose;
        this.objectKey = objectKey;
        this.bucket = bucket;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.status = "PENDING";
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getStatus() {
        return status;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markActive() {
        this.status = "ACTIVE";
    }

    public void markOrphaned() {
        this.status = "ORPHANED";
    }
}
