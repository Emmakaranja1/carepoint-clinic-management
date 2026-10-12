package com.carepoint.scheduling.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.carepoint.catalog.persistence.DoctorProfileRepository;
import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.scheduling.domain.DoctorSchedule;
import com.carepoint.scheduling.domain.DoctorTimeOff;
import com.carepoint.scheduling.persistence.AppointmentRangeReader;
import com.carepoint.scheduling.persistence.DoctorScheduleRepository;
import com.carepoint.scheduling.persistence.DoctorTimeOffRepository;

/**
 * Availability engine (docs/09 §2.3): schedules ∩ range − time-off −
 * booked appointments, emitted as UTC instants. Exactly three queries:
 * schedules, time-off overlaps, booked overlaps — none per-slot.
 */
@Service
public class AvailabilityService {

    private static final int MAX_RANGE_DAYS = 31;

    private final ClinicRepository clinics;
    private final DoctorProfileRepository profiles;
    private final DoctorScheduleRepository schedules;
    private final DoctorTimeOffRepository timeOff;
    private final AppointmentRangeReader appointments;

    public AvailabilityService(ClinicRepository clinics,
                               DoctorProfileRepository profiles,
                               DoctorScheduleRepository schedules,
                               DoctorTimeOffRepository timeOff,
                               AppointmentRangeReader appointments) {
        this.clinics = clinics;
        this.profiles = profiles;
        this.schedules = schedules;
        this.timeOff = timeOff;
        this.appointments = appointments;
    }

    public record Slot(Instant start, Instant end) {
    }

    public List<Slot> available(UUID clinicId, UUID doctorId, LocalDate from, LocalDate to) {
        Clinic clinic = clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        profiles.findByIdAndClinicId(doctorId, clinicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));

        ZoneId tz = ZoneId.of(clinic.getTimezone());
        LocalDate today = LocalDate.now(tz);
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("to", "from/to must be a valid date range"));
        }
        if (from.isBefore(today)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("from", "Must not be in the past"));
        }
        if (to.isAfter(from.plusDays(MAX_RANGE_DAYS))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("to", "Range is limited to " + MAX_RANGE_DAYS + " days"));
        }

        Instant rangeStart = from.atStartOfDay(tz).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(tz).toInstant();

        List<DoctorSchedule> windows = schedules.findByDoctorId(doctorId);
        List<Range> busy = new ArrayList<>();
        for (DoctorTimeOff t : timeOff.findByDoctorIdAndOffFromBeforeAndOffToAfter(
                doctorId, rangeEnd, rangeStart)) {
            busy.add(new Range(t.getOffFrom(), t.getOffTo()));
        }
        for (var b : appointments.bookedOverlapping(doctorId, rangeStart, rangeEnd)) {
            busy.add(new Range(b.start(), b.end()));
        }

        Instant now = Instant.now();
        List<Slot> slots = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            int dow = day.getDayOfWeek().getValue() - 1; // 0=Monday per docs/07
            for (DoctorSchedule s : windows) {
                if (s.getDayOfWeek() != dow
                        || day.isBefore(s.getValidFrom())
                        || (s.getValidTo() != null && day.isAfter(s.getValidTo()))) {
                    continue;
                }
                Duration step = Duration.ofMinutes(s.getSlotMinutes());
                Instant cursor = day.atTime(s.getStartTime()).atZone(tz).toInstant();
                Instant windowEnd = day.atTime(s.getEndTime()).atZone(tz).toInstant();
                while (!cursor.plus(step).isAfter(windowEnd)) {
                    Instant slotEnd = cursor.plus(step);
                    if (slotEnd.isAfter(now) && !overlaps(busy, cursor, slotEnd)) {
                        slots.add(new Slot(cursor, slotEnd));
                    }
                    cursor = slotEnd;
                }
            }
        }
        return slots;
    }

    private boolean overlaps(List<Range> busy, Instant start, Instant end) {
        for (Range b : busy) {
            if (start.isBefore(b.end()) && end.isAfter(b.start())) {
                return true;
            }
        }
        return false;
    }

    private record Range(Instant start, Instant end) {
    }
}
