package com.carepoint.clinic.application;

import java.security.SecureRandom;
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
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.identity.application.OtpService;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.identity.persistence.UserRepository;

@Service
public class ClinicService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> STAFF_ROLES = List.of("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN");

    private final ClinicRepository clinics;
    private final ClinicMembershipRepository memberships;
    private final UserRepository users;
    private final ClinicAuthorization authz;
    private final OtpService otp;
    private final PasswordEncoder passwords;

    public ClinicService(ClinicRepository clinics,
                         ClinicMembershipRepository memberships,
                         UserRepository users,
                         ClinicAuthorization authz,
                         OtpService otp,
                         PasswordEncoder passwords) {
        this.clinics = clinics;
        this.memberships = memberships;
        this.users = users;
        this.authz = authz;
        this.otp = otp;
        this.passwords = passwords;
    }

    /**
     * Self-serve onboarding: a verified user creates a clinic and becomes
     * its CLINIC_ADMIN. (docs/01 §4 lists this post-MVP; pulled forward as
     * an admin-provisioning path for the portfolio build.)
     */
    @Transactional
    public Clinic create(CarepointPrincipal principal, String name,
                         String timezone, Map<String, Object> address) {
        User creator = users.findById(principal.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
        if (!creator.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Verify your email before creating a clinic");
        }
        try {
            ZoneId.of(timezone); // validates IANA zone
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("timezone", "Must be a valid IANA timezone, e.g. Africa/Nairobi"));
        }

        Clinic clinic = clinics.save(new Clinic(name, uniqueSlug(name), timezone, address));
        memberships.save(new ClinicMembership(clinic.getId(), creator.getId(), "CLINIC_ADMIN"));
        return clinic;
    }

    /** The caller's memberships — used by the staff UI to pick a clinic. */
    public List<MembershipView> myClinics(CarepointPrincipal principal) {
        return memberships.findByUserIdAndStatus(principal.userId(), "ACTIVE").stream()
                .map(m -> new MembershipView(m.getId(), m.getClinicId(),
                        clinics.findById(m.getClinicId()).map(Clinic::getName).orElse("—"),
                        m.getRole()))
                .toList();
    }

    /** Clinic staff list — admin and receptionist may view. */
    public List<StaffView> listStaff(CarepointPrincipal principal, UUID clinicId) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN", "RECEPTIONIST");
        return memberships.findByClinicId(clinicId).stream()
                .map(m -> {
                    User u = users.findById(m.getUserId()).orElse(null);
                    return new StaffView(m.getId(), m.getUserId(),
                            u == null ? "—" : u.getEmail(),
                            u == null ? "—" : u.getFullName(),
                            m.getRole(), m.getStatus());
                })
                .toList();
    }

    /**
     * Admin-created staff (docs/01: staff never self-register). A new email
     * gets a generated unusable password plus a set-password code by mail;
     * an existing user just gains the membership.
     */
    @Transactional
    public StaffView inviteStaff(CarepointPrincipal principal, UUID clinicId,
                               String email, String fullName, String role, String phone) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        if (!STAFF_ROLES.contains(role)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("role", "Must be one of " + STAFF_ROLES));
        }
        Clinic clinic = clinics.findById(clinicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        User user = users.findByEmail(email)
                .orElseGet(() -> users.save(User.localUser(email,
                        passwords.encode(randomSecret()), fullName, phone)));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("email", "This account is disabled"));
        }
        if (memberships.findByUserIdAndClinicId(user.getId(), clinicId).isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("email", "Already a member of this clinic — edit their role instead"));
        }
        ClinicMembership m = memberships.save(
                new ClinicMembership(clinicId, user.getId(), role));
        memberships.flush();

        otp.send(email, user.getId(), "PASSWORD_RESET",
                "You were added as " + role + " at " + clinic.getName() + " on CarePoint.");
        return new StaffView(m.getId(), user.getId(), user.getEmail(),
                user.getFullName(), m.getRole(), m.getStatus());
    }

    /** Change a member's role or status — admin only, with lockout guards. */
    @Transactional
    public StaffView updateStaff(CarepointPrincipal principal, UUID clinicId,
                               UUID membershipId, String newRole, String newStatus) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        ClinicMembership m = memberships.findById(membershipId)
                .filter(mm -> mm.getClinicId().equals(clinicId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        if (newRole != null && !STAFF_ROLES.contains(newRole)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("role", "Must be one of " + STAFF_ROLES));
        }
        if (newStatus != null && !List.of("ACTIVE", "DISABLED").contains(newStatus)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("status", "Must be ACTIVE or DISABLED"));
        }
        if (m.getUserId().equals(principal.userId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("membership", "You cannot change your own role or status"));
        }
        boolean losingAdmin = "CLINIC_ADMIN".equals(m.getRole())
                && (newRole != null && !"CLINIC_ADMIN".equals(newRole)
                    || "DISABLED".equals(newStatus));
        if (losingAdmin
                && memberships.countByClinicIdAndRoleAndStatus(clinicId, "CLINIC_ADMIN", "ACTIVE") <= 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("role", "A clinic must keep at least one active admin"));
        }

        if (newRole != null) {
            m.changeRole(newRole);
        }
        if (newStatus != null) {
            m.setStatus(newStatus);
        }
        User u = users.findById(m.getUserId()).orElseThrow();
        return new StaffView(m.getId(), m.getUserId(), u.getEmail(), u.getFullName(),
                m.getRole(), m.getStatus());
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "clinic";
        }
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

    public record MembershipView(UUID membershipId, UUID clinicId, String clinicName, String role) {
    }

    public record StaffView(UUID membershipId, UUID userId, String email,
                            String fullName, String role, String status) {
    }
}
