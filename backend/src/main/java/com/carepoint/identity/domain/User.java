package com.carepoint.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(nullable = false)
    private String status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(String email, String fullName, String phone,
                 String passwordHash, String provider, boolean verified) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.authProvider = provider;
        this.emailVerified = verified;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Local password user — starts unverified until the email OTP passes. */
    public static User localUser(String email, String passwordHash, String fullName, String phone) {
        return new User(email, fullName, phone, passwordHash, "LOCAL", false);
    }

    /** OAuth user — no password; verified only if the provider attested the email. */
    public static User oauthUser(String email, String fullName, String provider, boolean emailVerified) {
        return new User(email, fullName, null, null, provider, emailVerified);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
