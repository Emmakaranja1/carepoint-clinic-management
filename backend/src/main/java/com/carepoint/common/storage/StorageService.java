package com.carepoint.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Object-storage port (ADR-0004). Local dev/test uses the local-disk
 * implementation; deployed envs swap in an S3-compatible (R2) one — callers
 * only ever see this interface, never provider details or credentials.
 */
public interface StorageService {

    /** Direct-upload grant bounded to a content type and byte ceiling. */
    record PresignedPut(String url, Map<String, String> headers, Instant expiresAt) {
    }

    record ObjectStat(boolean exists, long size) {
    }

    /** Logical bucket/provider identifier recorded on media rows. */
    String bucket();

    PresignedPut presignPut(String objectKey, String contentType,
                            long maxBytes, Duration ttl);

    ObjectStat stat(String objectKey);

    /** Open the stored object for server-side verification (magic bytes). */
    InputStream open(String objectKey) throws IOException;

    String publicUrl(String objectKey);

    void delete(String objectKey);
}
