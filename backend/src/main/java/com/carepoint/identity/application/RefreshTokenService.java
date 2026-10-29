package com.carepoint.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.common.config.AuthProperties;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.identity.domain.RefreshToken;
import com.carepoint.identity.persistence.RefreshTokenRepository;

/**
 * Opaque refresh tokens, SHA-256-hashed at rest, rotated on every use.
 * Rotation family semantics: a revoked token being presented means the
 * real holder's token was stolen → the entire family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final AuthProperties props;

    public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties props) {
        this.tokens = tokens;
        this.props = props;
    }

    @Transactional
    public Issued issue(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    private Issued issue(UUID userId, UUID familyId) {
        String raw = generate();
        RefreshToken t = new RefreshToken(
                userId, familyId, hash(raw), Instant.now().plus(props.sessionIdleTimeout()));
        tokens.save(t);
        return new Issued(raw, userId);
    }

    /**
     * Validates the presented token and returns a fresh one in the same
     * family. Reuse of a rotated (revoked) token revokes the whole family —
     * the classic refresh-token-theft tripwire.
     */
    @Transactional
    public Issued rotate(String presentedToken) {
        RefreshToken stored = tokens.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "Invalid refresh token"));

        if (stored.isRevoked()) {
            tokens.revokeFamily(stored.getFamilyId());
            throw new ApiException(ErrorCode.TOKEN_INVALID, "Invalid refresh token");
        }
        if (stored.isExpired(Instant.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Session timed out — log in again");
        }

        stored.revoke();
        return issue(stored.getUserId(), stored.getFamilyId());
    }

    @Transactional
    public void revokeSession(String presentedToken) {
        tokens.findByTokenHash(hash(presentedToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId()));
    }

    private static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Issued(String token, UUID userId) {
    }
}
