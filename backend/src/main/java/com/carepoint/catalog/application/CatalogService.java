package com.carepoint.catalog.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.catalog.domain.DoctorProfile;
import com.carepoint.catalog.domain.Specialty;
import com.carepoint.catalog.persistence.DoctorProfileRepository;
import com.carepoint.catalog.persistence.SpecialtyRepository;
import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.identity.domain.ClinicMembership;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.media.application.MediaService;

/**
 * Public discovery reads + staff writes for doctor profiles.
 * Contracts per docs/09 §2: offset pagination (max 50), one join query
 * per list endpoint, ACTIVE-only visibility.
 */
@Service
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final List<String> CLINICIAN_ROLES = List.of("DOCTOR", "CLINIC_ADMIN");

    private final ClinicRepository clinics;
    private final DoctorProfileRepository profiles;
    private final SpecialtyRepository specialties;
    private final ClinicMembershipRepository memberships;
    private final ClinicAuthorization authz;
    private final MediaService media;

    public CatalogService(ClinicRepository clinics,
                          DoctorProfileRepository profiles,
                          SpecialtyRepository specialties,
                          ClinicMembershipRepository memberships,
                          ClinicAuthorization authz,
                          MediaService media) {
        this.clinics = clinics;
        this.profiles = profiles;
        this.specialties = specialties;
        this.memberships = memberships;
        this.authz = authz;
        this.media = media;
    }

    // ---------- public discovery ----------

    public Page<Clinic> listClinics(String name, int page, int size) {
        Pageable p = pageable(page, size);
        Page<Clinic> result = (name == null || name.isBlank())
                ? clinics.findByStatusOrderByNameAscIdAsc("ACTIVE", p)
                : clinics.findByStatusAndNameStartingWithIgnoreCaseOrderByNameAscIdAsc(
                        "ACTIVE", name.trim(), p);
        attachLogos(result.getContent());
        return result;
    }

    public Clinic getClinic(UUID clinicId) {
        Clinic clinic = clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        attachLogos(List.of(clinic));
        return clinic;
    }

    /** One bounded IN query per page — never a per-row media lookup. */
    private void attachLogos(Collection<Clinic> page) {
        Map<UUID, String> logos = media.publicUrlsByPurpose("CLINIC_LOGO",
                page.stream().map(Clinic::getId).toList());
        page.forEach(c -> c.setLogoUrl(logos.get(c.getId())));
    }

    public Page<DoctorSummary> listDoctors(UUID clinicId, UUID specialtyId,
                                           String q, int page, int size) {
        getClinic(clinicId); // 404 on unknown/disabled clinic
        // empty q → "LIKE '%'" matches all; avoids nullable-param type inference
        Page<DoctorRow> rows = profiles.searchInClinic(clinicId, specialtyId,
                q == null ? "" : q.trim(), pageable(page, size));
        Map<UUID, String> photos = media.publicUrlsByPurpose("DOCTOR_PHOTO",
                rows.getContent().stream().map(DoctorRow::id).toList());
        return rows.map(r -> new DoctorSummary(r.id(), r.fullName(), r.specialty(),
                r.consultMinutes(), photos.get(r.id())));
    }

    public DoctorDetail getDoctor(UUID clinicId, UUID doctorId) {
        getClinic(clinicId);
        DoctorDetailRow r = profiles.detailInClinic(clinicId, doctorId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        String photo = media.publicUrlsByPurpose("DOCTOR_PHOTO", List.of(doctorId))
                .get(doctorId);
        return new DoctorDetail(r.id(), r.fullName(), r.specialty(), r.bio(),
                r.consultMinutes(), photo);
    }

    public List<Specialty> clinicSpecialties(UUID clinicId) {
        getClinic(clinicId);
        return specialties.findDistinctByClinicId(clinicId);
    }

    public List<Specialty> allSpecialties() {
        return specialties.findAllByOrderByNameAsc();
    }

    // ---------- staff writes ----------

    /** Profile is 1:1 with a clinician membership (DOCTOR or practicing admin). */
    @Transactional
    public DoctorProfile createProfile(CarepointPrincipal principal, UUID clinicId,
                                       UUID membershipId, UUID specialtyId,
                                       String bio, Integer consultMinutes) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        getClinic(clinicId);
        Specialty specialty = specialties.findById(specialtyId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Request validation failed", Map.of("specialtyId", "Unknown specialty")));
        ClinicMembership membership = memberships.findById(membershipId)
                .filter(m -> m.getClinicId().equals(clinicId) && m.isActive())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Request validation failed", Map.of("membershipId", "No such staff member")));
        if (!CLINICIAN_ROLES.contains(membership.getRole())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("membershipId", "Only DOCTOR or CLINIC_ADMIN members can have a doctor profile"));
        }
        if (profiles.existsByMembershipId(membershipId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("membershipId", "This member already has a doctor profile"));
        }
        return profiles.save(new DoctorProfile(
                clinicId, membershipId, specialty.getId(), bio, consultMinutes));
    }

    @Transactional
    public DoctorProfile updateProfile(CarepointPrincipal principal, UUID clinicId,
                                       UUID doctorId, UUID specialtyId,
                                       String bio, Integer consultMinutes) {
        authz.requireRole(principal, clinicId, "CLINIC_ADMIN");
        DoctorProfile profile = profiles.findByIdAndClinicId(doctorId, clinicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        if (specialtyId != null) {
            specialties.findById(specialtyId)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Request validation failed", Map.of("specialtyId", "Unknown specialty")));
        }
        profile.update(specialtyId != null ? specialtyId : profile.getSpecialtyId(),
                bio, consultMinutes);
        return profile;
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE));
    }

    // ---------- public API records (response shape) ----------

    public record DoctorSummary(UUID id, String fullName, String specialty,
                                Integer consultMinutes, String photoUrl) {
    }

    public record DoctorDetail(UUID id, String fullName, String specialty,
                               String bio, Integer consultMinutes, String photoUrl) {
    }

    // ---------- JPQL projection rows (internal) ----------

    public record DoctorRow(UUID id, String fullName, String specialty, Integer consultMinutes) {
    }

    public record DoctorDetailRow(UUID id, String fullName, String specialty,
                                  String bio, Integer consultMinutes) {
    }
}
