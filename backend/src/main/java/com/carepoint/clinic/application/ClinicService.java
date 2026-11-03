package com.carepoint.clinic.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.audit.AuditLog;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.ClinicMembershipRepository;

@Service
public class ClinicService {

    private static final List<String> STAFF_ROLES = List.of("DOCTOR", "RECEPTIONIST", "CLINIC_ADMIN");

    private final ClinicRepository clinics;
    private final ClinicMembershipRepository memberships;
    private final ClinicAuthorization authz;
    private final ClinicProvisioningService provisioning;
    private final StaffInvitationService invitations;
    private final StaffDirectory directory;
    private final AuditLog audit;

    public ClinicService(ClinicRepository clinics,
                         ClinicMembershipRepository memberships,
                         ClinicAuthorization authz,
                         ClinicProvisioningService provisioning,
                         StaffInvitationService invitations,
                         StaffDirectory directory,
                         AuditLog audit) {
        this.clinics = clinics;
        this.memberships = memberships;
        this.authz = authz;
        this.provisioning = provisioning;
        this.invitations = invitations;
        this.directory = directory;
        this.audit = audit;
    }

    /** The caller's memberships — used by the staff UI to pick a clinic. */
    public List<MembershipView> myClinics(CarepointPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        return memberships.findByUserIdAndStatus(principal.userId(), "ACTIVE").stream()
                .map(m -> new MembershipView(m.getId(), m.getClinicId(),
                        clinics.findById(m.getClinicId()).map(Clinic::getName).orElse("—"),
                        m.getRole()))
                .toList();
    }

    /**
     * Admin-created staff (docs/01: staff never self-register). The clinic
     * comes from the authorised path, never the body; the invitee sets their
     * own password through a single-use invitation link.
     */
    @Transactional
    public StaffDirectory.StaffMember inviteStaff(CarepointPrincipal principal, UUID clinicId,
                                                  String email, String fullName, String role, String phone) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        if (!STAFF_ROLES.contains(role)) {
            throw invalid("role", "Must be one of " + STAFF_ROLES);
        }
        Clinic clinic = clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        User user = provisioning.findOrCreateStaffUser(email.strip(), fullName.strip(), blankToNull(phone));
        if (!user.isActive()) {
            throw invalid("email", "This account is disabled");
        }
        if (!memberships.findByClinicIdAndUserId(clinicId, user.getId()).isEmpty()) {
            throw invalid("email", "Already a member of this clinic — edit their role instead");
        }
        ClinicMembership m;
        try {
            m = memberships.save(new ClinicMembership(clinicId, user.getId(), role));
            memberships.flush();
        } catch (DataIntegrityViolationException e) {
            throw invalid("email", "Already a member of this clinic — edit their role instead");
        }
        audit.record(clinicId, principal.userId(), "STAFF_ADDED", m.getId(), Map.of("role", role));
        invitations.issue(clinic, m, user, principal.userId());
        return directory.find(clinicId, m.getId()).orElseThrow();
    }

    /** Change a member's role or status — admin only, with lockout guards. */
    @Transactional
    public StaffDirectory.StaffMember updateStaff(CarepointPrincipal principal, UUID clinicId,
                                                  UUID membershipId, String newRole, String newStatus) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        ClinicMembership m = memberships.findById(membershipId)
                .filter(mm -> mm.getClinicId().equals(clinicId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        if (newRole != null && !STAFF_ROLES.contains(newRole)) {
            throw invalid("role", "Must be one of " + STAFF_ROLES);
        }
        if (newStatus != null && !List.of("ACTIVE", "DISABLED").contains(newStatus)) {
            throw invalid("status", "Must be ACTIVE or DISABLED");
        }
        if (m.getUserId().equals(principal.userId())) {
            throw invalid("membership", "You cannot change your own role or status");
        }
        boolean losingAdmin = "CLINIC_ADMIN".equals(m.getRole())
                && (newRole != null && !"CLINIC_ADMIN".equals(newRole)
                    || "DISABLED".equals(newStatus));
        if (losingAdmin
                && memberships.countByClinicIdAndRoleAndStatus(clinicId, "CLINIC_ADMIN", "ACTIVE") <= 1) {
            throw invalid("role", "A clinic must keep at least one active admin");
        }

        if (newRole != null && !newRole.equals(m.getRole())) {
            audit.record(clinicId, principal.userId(), "STAFF_ROLE_CHANGED", m.getId(),
                    Map.of("from", m.getRole(), "to", newRole));
            m.changeRole(newRole);
        }
        if (newStatus != null && !newStatus.equals(m.getStatus())) {
            audit.record(clinicId, principal.userId(), "STAFF_STATUS_CHANGED", m.getId(),
                    Map.of("role", m.getRole(), "from", m.getStatus(), "to", newStatus));
            m.setStatus(newStatus);
        }
        memberships.flush();
        return directory.find(clinicId, m.getId()).orElseThrow();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                Map.of(field, message));
    }

    public record MembershipView(UUID membershipId, UUID clinicId, String clinicName, String role) {
    }
}
