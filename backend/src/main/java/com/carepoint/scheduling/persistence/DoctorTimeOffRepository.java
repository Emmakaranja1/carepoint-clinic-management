package com.carepoint.scheduling.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carepoint.scheduling.domain.DoctorTimeOff;

public interface DoctorTimeOffRepository extends JpaRepository<DoctorTimeOff, UUID> {

    List<DoctorTimeOff> findByClinicIdAndDoctorIdOrderByOffFromAsc(UUID clinicId, UUID doctorId);

    /** All blocks overlapping [from, to) — the availability subtraction set. */
    List<DoctorTimeOff> findByDoctorIdAndOffFromBeforeAndOffToAfter(
            UUID doctorId, Instant to, Instant from);

    Optional<DoctorTimeOff> findByIdAndClinicIdAndDoctorId(
            UUID id, UUID clinicId, UUID doctorId);
}
