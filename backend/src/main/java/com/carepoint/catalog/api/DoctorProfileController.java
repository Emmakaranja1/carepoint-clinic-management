package com.carepoint.catalog.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.catalog.api.dto.CreateDoctorProfileRequest;
import com.carepoint.catalog.api.dto.DoctorProfileResponse;
import com.carepoint.catalog.api.dto.UpdateDoctorProfileRequest;
import com.carepoint.catalog.application.CatalogService;
import com.carepoint.catalog.domain.DoctorProfile;
import com.carepoint.common.security.CarepointPrincipal;

/** Staff-side doctor profile writes — CLINIC_ADMIN only. */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors")
public class DoctorProfileController {

    private final CatalogService catalog;

    public DoctorProfileController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping
    public ResponseEntity<DoctorProfileResponse> create(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @Valid @RequestBody CreateDoctorProfileRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(catalog.createProfile(principal, clinicId,
                        req.membershipId(), req.specialtyId(), req.bio(), req.consultMinutes())));
    }

    @PatchMapping("/{doctorId}")
    public DoctorProfileResponse update(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorId,
            @Valid @RequestBody UpdateDoctorProfileRequest req) {
        return toResponse(catalog.updateProfile(principal, clinicId, doctorId,
                req.specialtyId(), req.bio(), req.consultMinutes()));
    }

    private DoctorProfileResponse toResponse(DoctorProfile p) {
        return new DoctorProfileResponse(p.getId(), p.getMembershipId(),
                p.getSpecialtyId(), p.getBio(), p.getConsultMinutes());
    }
}
