package com.carepoint.booking.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carepoint.booking.domain.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
}
