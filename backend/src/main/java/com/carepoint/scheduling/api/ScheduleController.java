package com.carepoint.scheduling.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.scheduling.api.dto.TimeOffRequest;
import com.carepoint.scheduling.api.dto.UpsertScheduleRequest;
import com.carepoint.scheduling.application.ScheduleService;
import com.carepoint.scheduling.domain.DoctorSchedule;
import com.carepoint.scheduling.domain.DoctorTimeOff;

/** Staff schedule management — admin/receptionist or the doctor self. */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors/{doctorId}")
public class ScheduleController {

    private final ScheduleService schedules;

    public ScheduleController(ScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/schedule")
    public List<DoctorSchedule> getSchedule(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId, @PathVariable UUID doctorId) {
        return schedules.getSchedule(principal, clinicId, doctorId);
    }

    @PutMapping("/schedule")
    public List<DoctorSchedule> replaceSchedule(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId, @PathVariable UUID doctorId,
            @Valid @RequestBody UpsertScheduleRequest req) {
        return schedules.replaceSchedule(principal, clinicId, doctorId, req.toInputs());
    }

    @GetMapping("/time-off")
    public List<DoctorTimeOff> listTimeOff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId, @PathVariable UUID doctorId) {
        return schedules.listTimeOff(principal, clinicId, doctorId);
    }

    @PostMapping("/time-off")
    public ResponseEntity<DoctorTimeOff> addTimeOff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId, @PathVariable UUID doctorId,
            @Valid @RequestBody TimeOffRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(schedules.addTimeOff(principal, clinicId, doctorId,
                        req.offFrom(), req.offTo(), req.reason()));
    }

    @DeleteMapping("/time-off/{timeOffId}")
    public ResponseEntity<Void> deleteTimeOff(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId, @PathVariable UUID doctorId,
            @PathVariable UUID timeOffId) {
        schedules.deleteTimeOff(principal, clinicId, doctorId, timeOffId);
        return ResponseEntity.noContent().build();
    }
}
