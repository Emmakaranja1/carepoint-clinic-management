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
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.clinic.api.dto.ClinicResponse;
import com.carepoint.clinic.api.dto.CreateClinicRequest;
import com.carepoint.clinic.api.dto.InviteStaffRequest;
import com.carepoint.clinic.api.dto.UpdateStaffRequest;
import com.carepoint.clinic.application.ClinicService;
import com.carepoint.clinic.domain.Clinic;
import com.carepoint.common.security.CarepointPrincipal;

@RestController
@RequestMapping("/api/v1/clinics")
public class ClinicController {

    private final ClinicService clinics;

    public ClinicController(ClinicService clinics) {
        this.clinics = clinics;
    }

    @PostMapping
    public ResponseEntity<ClinicResponse> create(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @Valid @RequestBody CreateClinicRequest req) {
        Clinic clinic = clinics.create(principal, req.name(), req.timezone(), req.address());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ClinicResponse(clinic.getId(), clinic.getName(),
                        clinic.getSlug(), clinic.getTimezone(),
                        "Clinic created — you are its clinic admin"));
    }

    @GetMapping("/mine")
    public List<ClinicService.MembershipView> mine(
            @AuthenticationPrincipal CarepointPrincipal principal) {
        return clinics.myClinics(principal);
    }

    @GetMapping("/{clinicId}/staff")
    public List<ClinicService.StaffView> listStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId) {
        return clinics.listStaff(principal, clinicId);
    }

    @PostMapping("/{clinicId}/staff")
    public ResponseEntity<ClinicService.StaffView> inviteStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @Valid @RequestBody InviteStaffRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clinics.inviteStaff(principal, clinicId,
                        req.email(), req.fullName(), req.role(), req.phone()));
    }

    @PatchMapping("/{clinicId}/staff/{membershipId}")
    public ClinicService.StaffView updateStaff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateStaffRequest req) {
        return clinics.updateStaff(principal, clinicId, membershipId,
                req.role(), req.status());
    }
}
