package com.carepoint.media.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carepoint.media.domain.MediaObject;

public interface MediaObjectRepository extends JpaRepository<MediaObject, UUID> {

    Optional<MediaObject> findByIdAndClinicId(UUID id, UUID clinicId);

    List<MediaObject> findByStatusAndCreatedAtBefore(String status, Instant cutoff);
}
