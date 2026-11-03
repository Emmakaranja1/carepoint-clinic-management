package com.carepoint.identity.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carepoint.identity.domain.ClinicMembership;

public interface ClinicMembershipRepository extends JpaRepository<ClinicMembership, UUID> {

    List<ClinicMembership> findByUserIdAndStatus(UUID userId, String status);

    List<ClinicMembership> findByClinicIdAndUserId(UUID clinicId, UUID userId);

    long countByClinicIdAndRoleAndStatus(UUID clinicId, String role, String status);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM clinic_memberships m
                JOIN clinics c ON c.id = m.clinic_id
                WHERE m.user_id = :userId AND m.role = 'CLINIC_ADMIN'
                  AND m.status = 'ACTIVE' AND c.status = 'ACTIVE')
            """, nativeQuery = true)
    boolean hasActiveAdminMembership(@Param("userId") UUID userId);
}
