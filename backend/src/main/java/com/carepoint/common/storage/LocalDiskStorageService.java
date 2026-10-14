package com.carepoint.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.carepoint.common.config.AuthProperties;

/**
 * Dev/test storage (ADR-0004): bytes live under a local directory. The
 * "presigned" PUT is an HMAC capability URL handled by MediaController —
 * same shape as an S3 presign, so callers and tests exercise the real
 * contract without a cloud account.
 */
@Component
@ConditionalOnProperty(name = "carepoint.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorageService implements StorageService {

    private final Path root;
    private final String appBaseUrl;
    private final String publicBaseUrl;
    private final byte[] hmacKey;

    public LocalDiskStorageService(
            @Value("${carepoint.storage.local-dir}") String localDir,
            @Value("${carepoint.app-base-url}") String appBaseUrl,
            @Value("${carepoint.storage.public-base-url}") String publicBaseUrl,
            AuthProperties auth) throws IOException {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
        this.appBaseUrl = appBaseUrl.replaceAll("/$", "");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
        this.hmacKey = auth.jwtSigningKey().getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(root);
    }

    @Override
    public String bucket() {
        return "local";
    }

    /** HMAC over key|contentType|maxBytes|exp — the signature IS the grant. */
    public String signature(String key, String contentType, long maxBytes, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            String payload = key + "|" + contentType + "|" + maxBytes + "|" + exp;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public PresignedPut presignPut(String key, String contentType,
                                   long maxBytes, Duration ttl) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        String sig = signature(key, contentType, maxBytes, exp);
        // allowed image types contain no characters needing query encoding
        String url = appBaseUrl + "/api/v1/media/put/" + key
                + "?exp=" + exp
                + "&ct=" + contentType
                + "&max=" + maxBytes
                + "&sig=" + sig;
        return new PresignedPut(url, Map.of("Content-Type", contentType),
                Instant.ofEpochSecond(exp));
    }

    public Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("key escapes storage root");
        }
        return p;
    }

    @Override
    public ObjectStat stat(String key) {
        try {
            Path p = resolve(key);
            if (!Files.isRegularFile(p)) {
                return new ObjectStat(false, 0);
            }
            return new ObjectStat(true, Files.size(p));
        } catch (IOException e) {
            return new ObjectStat(false, 0);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // best-effort cleanup — the row's ORPHANED state is the record
        }
    }
}
