package com.carepoint.scheduling.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carepoint.scheduling.domain.DoctorSchedule;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

    List<DoctorSchedule> findByClinicIdAndDoctorIdOrderByDayOfWeekAscStartTimeAsc(
            UUID clinicId, UUID doctorId);

    List<DoctorSchedule> findByDoctorId(UUID doctorId);

    @Modifying
    @Query("DELETE FROM DoctorSchedule s WHERE s.clinicId = :clinicId AND s.doctorId = :doctorId")
    int deleteByClinicIdAndDoctorId(@Param("clinicId") UUID clinicId,
                                    @Param("doctorId") UUID doctorId);
}
