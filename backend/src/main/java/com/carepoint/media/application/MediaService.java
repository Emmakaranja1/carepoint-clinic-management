package com.carepoint.media.application;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.catalog.persistence.DoctorProfileRepository;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.common.storage.StorageService;
import com.carepoint.media.domain.MediaObject;
import com.carepoint.media.persistence.MediaObjectRepository;

/**
 * Media upload pipeline (docs/01 US-M1, docs/06 §7b): staff mint a bounded
 * presigned PUT, bytes land directly in storage, then `confirm` verifies
 * magic bytes + dimensions before the row goes ACTIVE. Only public-image
 * purposes exist today — no patient/clinical files, ever.
 */
@Service
public class MediaService {

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final Duration PUT_TTL = Duration.ofMinutes(10);

    private static final Set<String> PUBLIC_PURPOSES =
            Set.of("DOCTOR_PHOTO", "CLINIC_LOGO", "DEMO_IMAGE");

    private static final Map<String, String> EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/avif", "avif");

    private final ClinicAuthorization authz;
    private final ClinicRepository clinics;
    private final DoctorProfileRepository profiles;
    private final MediaObjectRepository media;
    private final StorageService storage;

    public MediaService(ClinicAuthorization authz, ClinicRepository clinics,
                        DoctorProfileRepository profiles,
                        MediaObjectRepository media, StorageService storage) {
        this.authz = authz;
        this.clinics = clinics;
        this.profiles = profiles;
        this.media = media;
        this.storage = storage;
    }

    public record MintedUpload(UUID id, String uploadUrl,
                               Map<String, String> headers,
                               Instant expiresAt, String publicUrl) {
    }

    public record MediaResponse(UUID id, UUID clinicId, String purpose,
                                String status, String publicUrl,
                                String contentType, long byteSize,
                                String ownerType, UUID ownerId) {
        static MediaResponse of(MediaObject m, StorageService storage) {
            return new MediaResponse(m.getId(), m.getClinicId(), m.getPurpose(),
                    m.getStatus(),
                    "ACTIVE".equals(m.getStatus()) ? storage.publicUrl(m.getObjectKey()) : null,
                    m.getContentType(), m.getByteSize(),
                    m.getOwnerType(), m.getOwnerId());
        }
    }

    @Transactional
    public MintedUpload mint(CarepointPrincipal principal, UUID clinicId,
                             String purpose, String ownerType, UUID ownerId,
                             String contentType, long byteSize) {
        authz.requireRole(principal, clinicId, "RECEPTIONIST", "CLINIC_ADMIN");
        clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        if (!PUBLIC_PURPOSES.contains(purpose)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("purpose", "Must be one of " + PUBLIC_PURPOSES));
        }
        if (!EXT.containsKey(contentType)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("contentType", "Allowed: " + EXT.keySet()));
        }
        if (byteSize < 1 || byteSize > MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("byteSize", "Must be 1–" + MAX_BYTES + " bytes"));
        }
        switch (purpose) {
            case "DOCTOR_PHOTO" -> {
                if (!"DOCTOR_PROFILE".equals(ownerType) || ownerId == null
                        || profiles.findByIdAndClinicId(ownerId, clinicId).isEmpty()) {
                    throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
                }
            }
            case "CLINIC_LOGO" -> {
                if (!"CLINIC".equals(ownerType) || !clinicId.equals(ownerId)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Request validation failed",
                            Map.of("ownerId", "Clinic logos belong to this clinic"));
                }
            }
            default -> {
                // DEMO_IMAGE — owner optional
            }
        }

        String key = "public/" + clinicId + "/" + UUID.randomUUID()
                + "." + EXT.get(contentType);
        MediaObject row = media.saveAndFlush(new MediaObject(clinicId, purpose, key,
                storage.bucket(), contentType, byteSize, ownerType, ownerId, principal.userId()));

        StorageService.PresignedPut put =
                storage.presignPut(key, contentType, byteSize, PUT_TTL);
        return new MintedUpload(row.getId(), put.url(), put.headers(),
                put.expiresAt(), storage.publicUrl(key));
    }

    /**
     * Confirm: the object must exist, fit the declared ceiling, and sniff as
     * the declared type (magic bytes — the Content-Type header is a claim,
     * not evidence). Failures orphan the row and delete the object.
     */
    @Transactional
    public MediaResponse confirm(CarepointPrincipal principal, UUID mediaId) {
        MediaObject m = media.findById(mediaId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        // membership check runs against the row's own clinic — outsiders 404
        authz.requireRole(principal, m.getClinicId(), "RECEPTIONIST", "CLINIC_ADMIN");
        if ("ACTIVE".equals(m.getStatus())) {
            return MediaResponse.of(m, storage); // idempotent
        }
        if (!"PENDING".equals(m.getStatus())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "This upload can no longer be confirmed");
        }

        StorageService.ObjectStat stat = storage.stat(m.getObjectKey());
        if (!stat.exists() || stat.size() == 0 || stat.size() > MAX_BYTES) {
            return fail(m, "Uploaded object missing or out of bounds");
        }
        try (InputStream in = storage.open(m.getObjectKey())) {
            byte[] head = in.readNBytes(16);
            if (!sniffMatches(head, m.getContentType())) {
                return fail(m, "File content does not match declared type " + m.getContentType());
            }
            checkDimensions(m);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            return fail(m, "Could not read uploaded object");
        }

        m.markActive();
        return MediaResponse.of(media.save(m), storage);
    }

    /**
     * Confirm failure: delete the object and surface the error. The row
     * stays PENDING (an ApiException rolls back this transaction anyway)
     * and the janitor reaps stale PENDING rows — the documented lifecycle.
     */
    private MediaResponse fail(MediaObject m, String reason) {
        storage.delete(m.getObjectKey());
        throw new ApiException(ErrorCode.VALIDATION_FAILED, reason,
                Map.of("file", reason));
    }

    static boolean sniffMatches(byte[] head, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> head.length >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF;
            case "image/png" -> head.length >= 4
                    && (head[0] & 0xFF) == 0x89 && head[1] == 'P'
                    && head[2] == 'N' && head[3] == 'G';
            case "image/webp" -> head.length >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            case "image/avif" -> head.length >= 12
                    && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p'
                    && ((head[8] == 'a' && head[9] == 'v' && head[10] == 'i'
                            && (head[11] == 'f' || head[11] == 's')));
            default -> false;
        };
    }

    /** Pixel-dimension cap for formats ImageIO can decode. */
    private void checkDimensions(MediaObject m) throws Exception {
        if ("image/avif".equals(m.getContentType())) {
            return; // ImageIO can't decode AVIF — magic bytes still enforced
        }
        try (InputStream in = storage.open(m.getObjectKey())) {
            var img = ImageIO.read(in);
            if (img == null || img.getWidth() > MAX_DIMENSION
                    || img.getHeight() > MAX_DIMENSION) {
                fail(m, "Image unreadable or exceeds " + MAX_DIMENSION + "px");
            }
        }
    }
}
