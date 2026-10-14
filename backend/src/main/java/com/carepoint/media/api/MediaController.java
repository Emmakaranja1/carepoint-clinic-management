package com.carepoint.media.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.storage.LocalDiskStorageService;
import com.carepoint.media.application.MediaService;

/** Media upload pipeline + local-provider storage endpoints (docs/06 §7b). */
@RestController
@RequestMapping("/api/v1")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    public record CreateUploadRequest(
            @NotNull String purpose,
            String ownerType,
            UUID ownerId,
            @Size(max = 255) String fileName,
            @NotNull String contentType,
            @Min(1) @Max(5 * 1024 * 1024) long byteSize) {
    }

    @PostMapping("/clinics/{clinicId}/media/uploads")
    public ResponseEntity<MediaService.MintedUpload> mint(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @Valid @RequestBody CreateUploadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(media.mint(principal, clinicId, req.purpose(), req.ownerType(),
                        req.ownerId(), req.contentType(), req.byteSize()));
    }

    @PostMapping("/media/{id}/confirm")
    public MediaService.MediaResponse confirm(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID id) {
        return media.confirm(principal, id);
    }

    /**
     * Local-storage sink and read — the dev/test half of the presigned-PUT
     * contract (ADR-0004). The HMAC-signed URL is the capability; there are
     * no cookies or sessions involved, which is why the CSRF filter exempts
     * this path.
     */
    @RestController
    @ConditionalOnProperty(name = "carepoint.storage.provider",
            havingValue = "local", matchIfMissing = true)
    public static class LocalStorageEndpoints {

        private final LocalDiskStorageService storage;

        LocalStorageEndpoints(LocalDiskStorageService storage) {
            this.storage = storage;
        }

        @PutMapping("/api/v1/media/put/**")
        public ResponseEntity<Void> put(HttpServletRequest req) throws IOException {
            String key = req.getRequestURI()
                    .substring(req.getRequestURI().indexOf("/put/") + 5);
            String exp = req.getParameter("exp");
            String ct = req.getParameter("ct");
            String max = req.getParameter("max");
            String sig = req.getParameter("sig");
            if (exp == null || ct == null || max == null || sig == null) {
                throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Malformed upload URL");
            }
            long expAt;
            long maxBytes;
            try {
                expAt = Long.parseLong(exp);
                maxBytes = Long.parseLong(max);
            } catch (NumberFormatException e) {
                throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Malformed upload URL");
            }
            if (expAt < Instant.now().getEpochSecond()
                    || !constantTimeEquals(storage.signature(key, ct, maxBytes, expAt), sig)) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Invalid or expired upload URL");
            }
            // compare media type/subtype — clients may append a charset
            String reqCt = req.getContentType();
            boolean typeMatches = reqCt != null
                    && MediaType.parseMediaType(reqCt)
                            .equalsTypeAndSubtype(MediaType.parseMediaType(ct));
            if (!typeMatches) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                        java.util.Map.of("Content-Type", "Must be " + ct));
            }

            Path target;
            try {
                target = storage.resolve(key);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Malformed upload URL");
            }
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".part");
            try (InputStream in = req.getInputStream();
                    OutputStream out = Files.newOutputStream(tmp)) {
                in.transferTo(new java.io.OutputStream() {
                    long n = 0;
                    final OutputStream sink = out;

                    @Override
                    public void write(int b) throws IOException {
                        write(new byte[]{(byte) b});
                    }

                    @Override
                    public void write(byte[] buf, int off, int len) throws IOException {
                        n += len;
                        if (n > maxBytes) {
                            throw new IOException("exceeds declared size");
                        }
                        sink.write(buf, off, len);
                    }
                });
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Upload exceeds the declared byte ceiling");
            }
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/media/local/**")
        public ResponseEntity<byte[]> read(HttpServletRequest req) throws IOException {
            String key = req.getRequestURI()
                    .substring(req.getRequestURI().indexOf("/local/") + 7);
            Path p;
            try {
                p = storage.resolve(key);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
            }
            if (!Files.isRegularFile(p)) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
            }
            String ct = Files.probeContentType(p);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            ct != null ? ct : "application/octet-stream"))
                    .header("Cache-Control", "public, max-age=31536000, immutable")
                    .body(Files.readAllBytes(p));
        }

        private static boolean constantTimeEquals(String a, String b) {
            return java.security.MessageDigest.isEqual(
                    a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
