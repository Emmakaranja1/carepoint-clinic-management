package com.carepoint.catalog.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carepoint.catalog.application.CatalogService;
import com.carepoint.catalog.domain.DoctorProfile;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

    Optional<DoctorProfile> findByIdAndClinicId(UUID id, UUID clinicId);

    boolean existsByMembershipId(UUID membershipId);

    /**
     * Discovery list — one join query (docs/09 §2.2), never per-row lookups.
     * Row = profile + doctor name + specialty name.
     */
    @Query("""
            SELECT new com.carepoint.catalog.application.CatalogService$DoctorRow(
                d.id, u.fullName, s.name, d.consultMinutes)
            FROM DoctorProfile d
            JOIN ClinicMembership m ON m.id = d.membershipId
            JOIN User u ON u.id = m.userId
            JOIN Specialty s ON s.id = d.specialtyId
            WHERE d.clinicId = :clinicId AND m.status = 'ACTIVE'
              AND (:specialtyId IS NULL OR d.specialtyId = :specialtyId)
              AND LOWER(u.fullName) LIKE LOWER(CONCAT(CAST(:q AS string), '%'))
            ORDER BY u.fullName, d.id
            """)
    Page<CatalogService.DoctorRow> searchInClinic(@Param("clinicId") UUID clinicId,
                                                  @Param("specialtyId") UUID specialtyId,
                                                  @Param("q") String q,
                                                  Pageable pageable);

    @Query("""
            SELECT new com.carepoint.catalog.application.CatalogService$DoctorDetailRow(
                d.id, u.fullName, s.name, d.bio, d.consultMinutes)
            FROM DoctorProfile d
            JOIN ClinicMembership m ON m.id = d.membershipId
            JOIN User u ON u.id = m.userId
            JOIN Specialty s ON s.id = d.specialtyId
            WHERE d.id = :doctorId AND d.clinicId = :clinicId AND m.status = 'ACTIVE'
            """)
    Optional<CatalogService.DoctorDetailRow> detailInClinic(@Param("clinicId") UUID clinicId,
                                                          @Param("doctorId") UUID doctorId);
}
