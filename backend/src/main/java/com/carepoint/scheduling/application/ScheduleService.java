package com.carepoint.scheduling.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.catalog.domain.DoctorProfile;
import com.carepoint.catalog.persistence.DoctorProfileRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.common.security.ClinicAuthorization;
import com.carepoint.identity.persistence.ClinicMembershipRepository;
import com.carepoint.scheduling.domain.DoctorSchedule;
import com.carepoint.scheduling.domain.DoctorTimeOff;
import com.carepoint.scheduling.persistence.DoctorScheduleRepository;
import com.carepoint.scheduling.persistence.DoctorTimeOffRepository;

/**
 * Staff-side schedule and time-off management (docs/02 staff flows).
 * Writers: CLINIC_ADMIN, RECEPTIONIST, or the doctor managing their own.
 */
@Service
public class ScheduleService {

    private static final Set<Integer> ALLOWED_SLOT_MINUTES =
            Set.of(10, 15, 20, 30, 45, 60);

    private final DoctorProfileRepository profiles;
    private final DoctorScheduleRepository schedules;
    private final DoctorTimeOffRepository timeOff;
    private final ClinicMembershipRepository memberships;
    private final ClinicAuthorization authz;

    public ScheduleService(DoctorProfileRepository profiles,
                           DoctorScheduleRepository schedules,
                           DoctorTimeOffRepository timeOff,
                           ClinicMembershipRepository memberships,
                           ClinicAuthorization authz) {
        this.profiles = profiles;
        this.schedules = schedules;
        this.timeOff = timeOff;
        this.memberships = memberships;
        this.authz = authz;
    }

    public record WindowInput(int dayOfWeek, LocalTime startTime, LocalTime endTime,
                              int slotMinutes, LocalDate validFrom, LocalDate validTo) {
    }

    public List<DoctorSchedule> getSchedule(CarepointPrincipal principal,
                                            UUID clinicId, UUID doctorId) {
        profileInClinic(principal, clinicId, doctorId); // enforces membership
        return schedules.findByClinicIdAndDoctorIdOrderByDayOfWeekAscStartTimeAsc(
                clinicId, doctorId);
    }

    /** Replaces the doctor's weekly template atomically. */
    @Transactional
    public List<DoctorSchedule> replaceSchedule(CarepointPrincipal principal,
                                                UUID clinicId, UUID doctorId,
                                                List<WindowInput> windows) {
        assertCanManageSchedule(principal, clinicId, doctorId);
        validateWindows(windows);
        schedules.deleteByClinicIdAndDoctorId(clinicId, doctorId);
        List<DoctorSchedule> saved = windows.stream()
                .map(w -> new DoctorSchedule(clinicId, doctorId, w.dayOfWeek(),
                        w.startTime(), w.endTime(), w.slotMinutes(),
                        w.validFrom() != null ? w.validFrom() : LocalDate.now(),
                        w.validTo()))
                .toList();
        return schedules.saveAll(saved);
    }

    public DoctorTimeOff addTimeOff(CarepointPrincipal principal, UUID clinicId,
                                    UUID doctorId, Instant offFrom, Instant offTo,
                                    String reason) {
        assertCanManageSchedule(principal, clinicId, doctorId);
        if (offFrom == null || offTo == null || !offTo.isAfter(offFrom)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("offTo", "offTo must be after offFrom"));
        }
        return timeOff.save(new DoctorTimeOff(clinicId, doctorId, offFrom, offTo, reason));
    }

    public List<DoctorTimeOff> listTimeOff(CarepointPrincipal principal,
                                           UUID clinicId, UUID doctorId) {
        profileInClinic(principal, clinicId, doctorId);
        return timeOff.findByClinicIdAndDoctorIdOrderByOffFromAsc(clinicId, doctorId);
    }

    @Transactional
    public void deleteTimeOff(CarepointPrincipal principal, UUID clinicId,
                              UUID doctorId, UUID timeOffId) {
        assertCanManageSchedule(principal, clinicId, doctorId);
        DoctorTimeOff t = timeOff.findByIdAndClinicIdAndDoctorId(timeOffId, clinicId, doctorId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        timeOff.delete(t);
    }

    // ---------- internals ----------

    /** 404 if the profile is absent/cross-clinic; enforces clinic membership. */
    private DoctorProfile profileInClinic(CarepointPrincipal principal,
                                          UUID clinicId, UUID doctorId) {
        authz.requireMembership(principal, clinicId);
        return profiles.findByIdAndClinicId(doctorId, clinicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
    }

    /** Admin/receptionist always; a DOCTOR only for their own profile. */
    private void assertCanManageSchedule(CarepointPrincipal principal,
                                         UUID clinicId, UUID doctorId) {
        DoctorProfile profile = profileInClinic(principal, clinicId, doctorId);
        String role = principal.clinicRoles().get(clinicId);
        if ("CLINIC_ADMIN".equals(role) || "RECEPTIONIST".equals(role)) {
            return;
        }
        boolean ownProfile = memberships.findById(profile.getMembershipId())
                .map(m -> m.getUserId().equals(principal.userId()))
                .orElse(false);
        if (!ownProfile) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access denied");
        }
    }

    private void validateWindows(List<WindowInput> windows) {
        List<WindowInput> sorted = windows.stream()
                .sorted(Comparator.comparing(WindowInput::dayOfWeek)
                        .thenComparing(WindowInput::startTime))
                .toList();
        WindowInput prev = null;
        for (WindowInput w : sorted) {
            if (w.dayOfWeek() < 0 || w.dayOfWeek() > 6) {
                throw fieldError("dayOfWeek", "Must be 0 (Monday) to 6 (Sunday)");
            }
            if (w.startTime() == null || w.endTime() == null || !w.endTime().isAfter(w.startTime())) {
                throw fieldError("endTime", "endTime must be after startTime");
            }
            if (!ALLOWED_SLOT_MINUTES.contains(w.slotMinutes())) {
                throw fieldError("slotMinutes", "Must be one of " + ALLOWED_SLOT_MINUTES);
            }
            if (w.validTo() != null && w.validFrom() != null && !w.validTo().isAfter(w.validFrom())) {
                throw fieldError("validTo", "validTo must be after validFrom");
            }
            if (prev != null && prev.dayOfWeek() == w.dayOfWeek()
                    && w.startTime().isBefore(prev.endTime())) {
                throw fieldError("windows",
                        "Overlapping windows on day " + w.dayOfWeek());
            }
            prev = w;
        }
    }

    private ApiException fieldError(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED,
                "Request validation failed", Map.of(field, message));
    }
}
