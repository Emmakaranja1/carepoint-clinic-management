package com.carepoint.clinic.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.carepoint.clinic.domain.Clinic;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    boolean existsBySlug(String slug);

    Optional<Clinic> findBySlug(String slug);

    Page<Clinic> findByStatusOrderByNameAscIdAsc(String status, Pageable pageable);

    Page<Clinic> findByStatusAndNameStartingWithIgnoreCaseOrderByNameAscIdAsc(
            String status, String name, Pageable pageable);
}
