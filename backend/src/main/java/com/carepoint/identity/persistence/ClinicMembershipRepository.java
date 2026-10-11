package com.carepoint.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carepoint.identity.domain.ClinicMembership;

public interface ClinicMembershipRepository extends JpaRepository<ClinicMembership, UUID> {

    List<ClinicMembership> findByUserIdAndStatus(UUID userId, String status);

    Optional<ClinicMembership> findByUserIdAndClinicId(UUID userId, UUID clinicId);

    List<ClinicMembership> findByClinicId(UUID clinicId);

    long countByClinicIdAndRoleAndStatus(UUID clinicId, String role, String status);
}
