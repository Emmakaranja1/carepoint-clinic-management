package com.carepoint.identity.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carepoint.identity.domain.ClinicMembership;

public interface ClinicMembershipRepository extends JpaRepository<ClinicMembership, UUID> {

    List<ClinicMembership> findByUserIdAndStatus(UUID userId, String status);
}
