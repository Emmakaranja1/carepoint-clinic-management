package com.carepoint.booking.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carepoint.booking.domain.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdAndClinicId(UUID id, UUID clinicId);

    /** Own-appointments reads — patients never see foreign rows. */
    // Native queries must project the @Formula read aliases explicitly —
    // Hibernate expects literal "slotStart"/"slotEnd" column labels.
    @Query(value = """
            SELECT *, lower(slot) AS "slotStart", upper(slot) AS "slotEnd"
            FROM appointments
            WHERE patient_id = :patientId AND status = 'BOOKED' AND lower(slot) >= now()
            ORDER BY lower(slot)
            """, nativeQuery = true)
    List<Appointment> findUpcoming(@Param("patientId") UUID patientId);

    @Query(value = """
            SELECT *, lower(slot) AS "slotStart", upper(slot) AS "slotEnd"
            FROM appointments
            WHERE patient_id = :patientId AND (status <> 'BOOKED' OR lower(slot) < now())
            ORDER BY lower(slot) DESC
            """, nativeQuery = true)
    List<Appointment> findPastOrInactive(@Param("patientId") UUID patientId);
}
