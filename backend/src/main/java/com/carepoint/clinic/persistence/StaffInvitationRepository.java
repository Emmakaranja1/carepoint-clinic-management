package com.carepoint.clinic.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carepoint.clinic.domain.StaffInvitation;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    /** Row lock serialises concurrent accepts of the same token — exactly one wins. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM StaffInvitation i WHERE i.tokenHash = :hash")
    Optional<StaffInvitation> lockByTokenHash(@Param("hash") String tokenHash);

    Optional<StaffInvitation> findTopByMembershipIdOrderByCreatedAtDesc(UUID membershipId);

    boolean existsByMembershipIdAndConsumedAtIsNotNull(UUID membershipId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE StaffInvitation i SET i.revokedAt = CURRENT_TIMESTAMP "
            + "WHERE i.membershipId = :membershipId "
            + "AND i.consumedAt IS NULL AND i.revokedAt IS NULL")
    int revokeOpen(@Param("membershipId") UUID membershipId);
}
