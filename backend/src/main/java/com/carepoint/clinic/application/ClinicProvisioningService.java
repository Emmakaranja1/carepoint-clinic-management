package com.carepoint.clinic.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.clinic.persistence.StaffInvitationRepository;
import com.carepoint.common.audit.AuditLog;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.identity.persistence.UserRepository;

/**
 * Operator-only clinic provisioning (docs/01 §2, ADR-0003: tenants are
 * provisioned manually). There is deliberately no HTTP entry point — it is
 * driven by {@link AdminBootstrapRunner} from server-side configuration.
 */
@Service
public class ClinicProvisioningService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public enum AdminProvisioning { INVITED, INVITATION_PENDING, ALREADY_ACTIVE }

    private final ClinicRepository clinics;
    private final ClinicMembershipRepository memberships;
    private final UserRepository users;
    private final StaffInvitationRepository invitations;
    private final StaffInvitationService invitationService;
    private final PasswordEncoder passwords;
    private final AuditLog audit;

    public ClinicProvisioningService(ClinicRepository clinics,
                                     ClinicMembershipRepository memberships,
                                     UserRepository users,
                                     StaffInvitationRepository invitations,
                                     StaffInvitationService invitationService,
                                     PasswordEncoder passwords,
                                     AuditLog audit) {
        this.clinics = clinics;
        this.memberships = memberships;
        this.users = users;
        this.invitations = invitations;
        this.invitationService = invitationService;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional
    public Clinic createClinic(String name, String timezone, Map<String, Object> address) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("timezone", "Must be a valid IANA timezone, e.g. Africa/Nairobi"));
        }
        return clinics.save(new Clinic(name, uniqueSlug(name), timezone, address));
    }

    @Transactional
    public Clinic findOrCreateClinic(String name, String timezone) {
        return clinics.findBySlug(baseSlug(name))
                .orElseGet(() -> createClinic(name, timezone, null));
    }

    /**
     * Idempotent: grants CLINIC_ADMIN at the clinic and mails an invitation
     * unless the admin already accepted one or holds an unexpired one.
     */
    @Transactional
    public AdminProvisioning ensureAdmin(UUID clinicId, String email, String fullName) {
        Clinic clinic = clinics.findById(clinicId)
                .orElseThrow(() -> new IllegalStateException("Unknown clinic " + clinicId));
        User admin = findOrCreateStaffUser(email, fullName, null);
        if (!admin.isActive()) {
            throw new IllegalStateException("Admin account is disabled — re-enable it manually");
        }
        List<ClinicMembership> existing = memberships.findByClinicIdAndUserId(clinicId, admin.getId());
        ClinicMembership membership = existing.stream()
                .filter(m -> "CLINIC_ADMIN".equals(m.getRole()))
                .findFirst()
                .orElseGet(() -> {
                    if (!existing.isEmpty()) {
                        throw new IllegalStateException(
                                "User already holds a different role at this clinic — change it via the admin UI");
                    }
                    ClinicMembership created = memberships.save(
                            new ClinicMembership(clinicId, admin.getId(), "CLINIC_ADMIN"));
                    memberships.flush();
                    audit.record(clinicId, admin.getId(), "CLINIC_ADMIN_PROVISIONED", created.getId(),
                            Map.of("role", "CLINIC_ADMIN"));
                    return created;
                });
        if (!membership.isActive()) {
            throw new IllegalStateException("Admin membership is disabled — re-enable it manually");
        }
        if (invitations.existsByMembershipIdAndConsumedAtIsNotNull(membership.getId())) {
            return AdminProvisioning.ALREADY_ACTIVE;
        }
        boolean pending = invitations.findTopByMembershipIdOrderByCreatedAtDesc(membership.getId())
                .filter(i -> i.isOpen(Instant.now()))
                .isPresent();
        if (pending) {
            return AdminProvisioning.INVITATION_PENDING;
        }
        invitationService.issue(clinic, membership, admin, admin.getId());
        return AdminProvisioning.INVITED;
    }

    /** New accounts get an unusable random password — the invitation link sets the real one. */
    User findOrCreateStaffUser(String email, String fullName, String phone) {
        return users.findByEmail(email).orElseGet(() -> users.save(
                User.localUser(email, passwords.encode(randomSecret()), fullName, phone)));
    }

    private static String baseSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return base.isBlank() ? "clinic" : base;
    }

    private String uniqueSlug(String name) {
        String base = baseSlug(name);
        String slug = base;
        for (int i = 2; clinics.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private static String randomSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
