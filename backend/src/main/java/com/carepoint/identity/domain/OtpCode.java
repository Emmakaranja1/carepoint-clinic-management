package com.carepoint.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "otp_codes")
public class OtpCode {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OtpCode() {
    }

    public OtpCode(String email, UUID userId, String purpose, String codeHash,
                   Instant expiresAt, int sendCount) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.userId = userId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.sendCount = sendCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getSendCount() {
        return sendCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public void recordAttempt() {
        this.attempts++;
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
