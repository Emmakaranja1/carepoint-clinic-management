package com.carepoint.catalog.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carepoint.catalog.domain.Specialty;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

    List<Specialty> findAllByOrderByNameAsc();

    /** Specialties actually offered by a clinic's doctors — the filter facet. */
    @Query("SELECT DISTINCT s FROM Specialty s JOIN DoctorProfile d ON d.specialtyId = s.id "
            + "WHERE d.clinicId = :clinicId ORDER BY s.name")
    List<Specialty> findDistinctByClinicId(@Param("clinicId") UUID clinicId);
}
