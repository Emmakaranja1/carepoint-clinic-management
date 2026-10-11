package com.carepoint.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.identity.domain.OtpCode;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, String purpose);

    /**
     * REQUIRES_NEW: a wrong code ends in an ApiException that rolls back the
     * caller's transaction — the failed attempt must still be counted.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE OtpCode o SET o.attempts = o.attempts + 1 WHERE o.id = :id")
    int recordAttempt(@Param("id") UUID id);
}
