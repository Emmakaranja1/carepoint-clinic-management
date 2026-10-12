package com.carepoint.catalog.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.catalog.application.CatalogService;
import com.carepoint.catalog.domain.Specialty;
import com.carepoint.clinic.domain.Clinic;

import java.util.concurrent.TimeUnit;

/**
 * Public discovery endpoints — no auth required (docs/09 §2).
 * Short public caching on list reads; details stay fresh.
 */
@RestController
@RequestMapping("/api/v1")
public class DiscoveryController {

    private static final CacheControl LIST_CACHE = CacheControl
            .maxAge(5, TimeUnit.MINUTES).cachePublic();

    private final CatalogService catalog;

    public DiscoveryController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/clinics")
    public ResponseEntity<Page<Clinic>> clinics(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(LIST_CACHE)
                .body(catalog.listClinics(name, page, size));
    }

    @GetMapping("/clinics/{clinicId}")
    public Clinic clinic(@PathVariable UUID clinicId) {
        return catalog.getClinic(clinicId);
    }

    @GetMapping("/clinics/{clinicId}/doctors")
    public ResponseEntity<Page<CatalogService.DoctorSummary>> doctors(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) UUID specialtyId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(LIST_CACHE)
                .body(catalog.listDoctors(clinicId, specialtyId, q, page, size));
    }

    @GetMapping("/clinics/{clinicId}/doctors/{doctorId}")
    public CatalogService.DoctorDetail doctor(@PathVariable UUID clinicId,
                                              @PathVariable UUID doctorId) {
        return catalog.getDoctor(clinicId, doctorId);
    }

    @GetMapping("/clinics/{clinicId}/specialties")
    public ResponseEntity<List<Specialty>> specialties(@PathVariable UUID clinicId) {
        return ResponseEntity.ok().cacheControl(LIST_CACHE)
                .body(catalog.clinicSpecialties(clinicId));
    }

    /** Global specialty catalog — powers the profile-create + filter UIs. */
    @GetMapping("/specialties")
    public ResponseEntity<List<Specialty>> allSpecialties() {
        return ResponseEntity.ok().cacheControl(LIST_CACHE)
                .body(catalog.allSpecialties());
    }
}
