package com.carepoint.clinic.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.domain.StaffInvitation;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.clinic.persistence.StaffInvitationRepository;
import com.carepoint.common.audit.AuditLog;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.mail.MailService;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.identity.application.PasswordPolicy;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.identity.persistence.RefreshTokenRepository;
import com.carepoint.identity.persistence.UserRepository;

/**
 * Single-use, expiring staff invitation links. Only the SHA-256 of a token
 * is persisted; the raw token exists in the outgoing mail and nowhere else.
 */
@Service
public class StaffInvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffInvitationRepository invitations;
    private final ClinicMembershipRepository memberships;
    private final ClinicRepository clinics;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final ClinicAuthorization authz;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwords;
    private final MailService mail;
    private final AuditLog audit;
    private final Duration ttl;
    private final String frontendBaseUrl;

    public StaffInvitationService(StaffInvitationRepository invitations,
                                  ClinicMembershipRepository memberships,
                                  ClinicRepository clinics,
                                  UserRepository users,
                                  RefreshTokenRepository refreshTokens,
                                  ClinicAuthorization authz,
                                  PasswordPolicy passwordPolicy,
                                  PasswordEncoder passwords,
                                  MailService mail,
                                  AuditLog audit,
                                  @Value("${carepoint.invitations.ttl}") Duration ttl,
                                  @Value("${carepoint.frontend-base-url}") String frontendBaseUrl) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.clinics = clinics;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.authz = authz;
        this.passwordPolicy = passwordPolicy;
        this.passwords = passwords;
        this.mail = mail;
        this.audit = audit;
        this.ttl = ttl;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
    }

    public record InvitationPreview(String clinicName, String email, String fullName,
                                    String role, Instant expiresAt) {
    }

    /** Revokes any open invitation for the membership and mails a fresh link. */
    @Transactional
    public void issue(Clinic clinic, ClinicMembership membership, User invitee, UUID issuedBy) {
        invitations.revokeOpen(membership.getId());
        String token = newToken();
        StaffInvitation invitation = invitations.save(new StaffInvitation(
                clinic.getId(), membership.getId(), hash(token), Instant.now().plus(ttl), issuedBy));
        invitations.flush();
        mail.send(invitee.getEmail(), "You're invited to " + clinic.getName() + " on CarePoint",
                body(clinic.getName(), membership.getRole(), token));
        audit.record(clinic.getId(), issuedBy, "STAFF_INVITATION_SENT", membership.getId(),
                Map.of("role", membership.getRole(), "invitationId", invitation.getId()));
    }

    @Transactional
    public void resend(CarepointPrincipal principal, UUID clinicId, UUID membershipId) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        ClinicMembership membership = memberships.findById(membershipId)
                .filter(m -> m.getClinicId().equals(clinicId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        if (!membership.isActive()) {
            throw invalid("membership", "Reactivate this staff member before re-sending an invitation");
        }
        if (invitations.existsByMembershipIdAndConsumedAtIsNotNull(membershipId)) {
            throw invalid("membership", "This invitation was already accepted");
        }
        User invitee = users.findById(membership.getUserId()).filter(User::isActive)
                .orElseThrow(() -> invalid("membership", "This account is disabled"));
        Clinic clinic = clinics.findById(clinicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        issue(clinic, membership, invitee, principal.userId());
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token) {
        StaffInvitation invitation = invitations.findByTokenHash(hash(token))
                .filter(i -> i.isOpen(Instant.now()))
                .orElseThrow(StaffInvitationService::invalidToken);
        ClinicMembership membership = activeMembership(invitation);
        User user = users.findById(membership.getUserId()).filter(User::isActive)
                .orElseThrow(StaffInvitationService::invalidToken);
        String clinicName = clinics.findById(invitation.getClinicId())
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .map(Clinic::getName)
                .orElseThrow(StaffInvitationService::invalidToken);
        return new InvitationPreview(clinicName, user.getEmail(), user.getFullName(),
                membership.getRole(), invitation.getExpiresAt());
    }

    /** Sets the invitee's password, consumes the token and ends any live sessions. */
    @Transactional
    public void accept(String token, String password, String confirmPassword) {
        var violations = passwordPolicy.violations(password);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password does not meet requirements",
                    Map.of("password", passwordPolicy.describe(violations)));
        }
        if (!password.equals(confirmPassword)) {
            throw invalid("confirmPassword", "Passwords do not match");
        }
        Instant now = Instant.now();
        StaffInvitation invitation = invitations.lockByTokenHash(hash(token))
                .filter(i -> i.isOpen(now))
                .orElseThrow(StaffInvitationService::invalidToken);
        ClinicMembership membership = activeMembership(invitation);
        User user = users.findById(membership.getUserId()).filter(User::isActive)
                .orElseThrow(StaffInvitationService::invalidToken);
        clinics.findById(invitation.getClinicId())
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(StaffInvitationService::invalidToken);

        user.changePassword(passwords.encode(password));
        user.markEmailVerified();
        refreshTokens.revokeAllForUser(user.getId());
        invitation.consume(now);
        audit.record(invitation.getClinicId(), user.getId(), "STAFF_INVITATION_ACCEPTED",
                membership.getId(), Map.of("role", membership.getRole()));
    }

    private ClinicMembership activeMembership(StaffInvitation invitation) {
        return memberships.findById(invitation.getMembershipId())
                .filter(ClinicMembership::isActive)
                .orElseThrow(StaffInvitationService::invalidToken);
    }

    private String body(String clinicName, String role, String token) {
        return "You've been added as " + roleLabel(role) + " at " + clinicName + " on CarePoint."
                + "\n\nSet your password to activate your account:\n"
                + frontendBaseUrl + "/invite/accept#token=" + token
                + "\n\nThis link can be used once and expires in " + ttl.toHours() + " hours."
                + "\nIf you weren't expecting this invitation, you can ignore this email.";
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "CLINIC_ADMIN" -> "Clinic Administrator";
            case "DOCTOR" -> "Doctor";
            default -> "Receptionist";
        };
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(ErrorCode.INVITATION_INVALID,
                "This invitation link is invalid or has expired — ask your clinic administrator for a new one");
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                Map.of(field, message));
    }
}
