package com.carepoint.clinic.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.clinic.api.dto.InviteStaffRequest;
import com.carepoint.clinic.api.dto.UpdateStaffRequest;
import com.carepoint.clinic.application.ClinicAdminService;
import com.carepoint.clinic.application.ClinicService;
import com.carepoint.clinic.application.StaffDirectory;
import com.carepoint.clinic.application.StaffInvitationService;
import com.carepoint.common.security.CarepointPrincipal;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicController {

    private final ClinicService clinics;
    private final StaffDirectory directory;
    private final StaffInvitationService invitations;
    private final ClinicAdminService admin;

    public ClinicController(ClinicService clinics, StaffDirectory directory,
                            StaffInvitationService invitations, ClinicAdminService admin) {
        this.clinics = clinics;
        this.directory = directory;
        this.invitations = invitations;
        this.admin = admin;
    }

    @GetMapping("/mine")
    public List<ClinicService.MembershipView> mine(
            @AuthenticationPrincipal CarepointPrincipal principal) {
        return clinics.myClinics(principal);
    }

    @GetMapping("/{clinicId}/admin/overview")
    public ClinicAdminService.Overview overview(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId) {
        return admin.overview(principal, clinicId);
    }

    @GetMapping("/{clinicId}/admin/activity")
    public ClinicAdminService.ActivityPage activity(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return admin.activity(principal, clinicId, page, size);
    }

    @GetMapping("/{clinicId}/staff")
    public StaffDirectory.StaffPage listStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return directory.list(principal, clinicId, role, status, q, page, size);
    }

    @GetMapping("/{clinicId}/staff/{membershipId}")
    public StaffDirectory.StaffMember getStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID membershipId) {
        return directory.get(principal, clinicId, membershipId);
    }

    @PostMapping("/{clinicId}/staff")
    public ResponseEntity<StaffDirectory.StaffMember> inviteStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @Valid @RequestBody InviteStaffRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clinics.inviteStaff(principal, clinicId,
                        req.email(), req.fullName(), req.role(), req.phone()));
    }

    @PostMapping("/{clinicId}/staff/{membershipId}/invitation")
    public ResponseEntity<Void> resendInvitation(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID membershipId) {
        invitations.resend(principal, clinicId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{clinicId}/staff/{membershipId}")
    public StaffDirectory.StaffMember updateStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateStaffRequest req) {
        return clinics.updateStaff(principal, clinicId, membershipId,
                req.role(), req.status());
    }
}
