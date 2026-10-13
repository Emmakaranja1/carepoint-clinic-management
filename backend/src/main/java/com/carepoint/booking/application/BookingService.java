package com.carepoint.booking.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carepoint.booking.domain.Appointment;
import com.carepoint.booking.domain.IdempotencyKey;
import com.carepoint.booking.persistence.AppointmentRepository;
import com.carepoint.booking.persistence.IdempotencyKeyRepository;
import com.carepoint.catalog.persistence.DoctorProfileRepository;
import com.carepoint.clinic.domain.Clinic;
import com.carepoint.clinic.persistence.ClinicRepository;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.identity.domain.User;
import com.carepoint.identity.persistence.UserRepository;
import com.carepoint.scheduling.application.AvailabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Patient booking (docs/01 US-B1..B3). Double-booking protection lives in
 * the `no_overlapping_bookings` exclusion constraint — violations surface
 * as SQLState 23P01 (and 40P01 deadlock losers), both mapped to 409.
 */
@Service
public class BookingService {

    private static final Duration KEY_TTL = Duration.ofHours(24);

    private final AppointmentRepository appointments;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final ClinicRepository clinics;
    private final DoctorProfileRepository profiles;
    private final UserRepository users;
    private final AvailabilityService availability;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public BookingService(AppointmentRepository appointments,
                          IdempotencyKeyRepository idempotencyKeys,
                          ClinicRepository clinics,
                          DoctorProfileRepository profiles,
                          UserRepository users,
                          AvailabilityService availability,
                          JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.idempotencyKeys = idempotencyKeys;
        this.clinics = clinics;
        this.profiles = profiles;
        this.users = users;
        this.availability = availability;
        this.jdbc = jdbc;
    }

    public record AppointmentView(UUID id, UUID clinicId, UUID doctorId,
                                  String status, Instant start, Instant end,
                                  String reason, Instant cancelledAt) {
        static AppointmentView of(Appointment a) {
            return new AppointmentView(a.getId(), a.getClinicId(), a.getDoctorId(),
                    a.getStatus(), a.getSlotStart(), a.getSlotEnd(),
                    a.getReason(), a.getCancelledAt());
        }
    }

    /** Booking + idempotency + constraint handling — one transaction. */
    @Transactional
    public AppointmentView book(CarepointPrincipal principal, UUID clinicId,
                                UUID doctorId, Instant start, Instant end,
                                String reason, UUID idempotencyKey) {
        User patient = users.findById(principal.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required"));
        if (!patient.isEmailVerified()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Verify your email before booking");
        }
        Clinic clinic = clinics.findById(clinicId)
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
        if (profiles.findByIdAndClinicId(doctorId, clinicId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("end", "end must be after start"));
        }

        if (idempotencyKey != null) {
            String requestHash = hash(clinicId, doctorId, start, end, reason);
            Optional<AppointmentView> replay = checkKey(idempotencyKey, patient.getId(), requestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
            return bookAndStore(clinic, doctorId, patient.getId(), start, end,
                    reason, idempotencyKey, requestHash);
        }
        return bookAndStore(clinic, doctorId, patient.getId(), start, end,
                reason, null, null);
    }

    public List<AppointmentView> mine(CarepointPrincipal principal, String when) {
        if (!"upcoming".equals(when) && !"past".equals(when)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    Map.of("when", "Must be 'upcoming' or 'past'"));
        }
        if ("past".equals(when)) {
            return appointments.findPastOrInactive(principal.userId())
                    .stream().map(AppointmentView::of).toList();
        }
        return appointments.findUpcoming(principal.userId())
                .stream().map(AppointmentView::of).toList();
    }

    public AppointmentView get(CarepointPrincipal principal, UUID id) {
        return AppointmentView.of(ownAppointment(principal, id));
    }

    /**
     * Idempotent cancel: cancelling an already-cancelled appointment returns
     * its current state (docs/01 US-B3). Cutoff is per-clinic.
     */
    @Transactional
    public AppointmentView cancel(CarepointPrincipal principal, UUID id) {
        Appointment a = ownAppointment(principal, id);
        if ("CANCELLED".equals(a.getStatus())) {
            return AppointmentView.of(a);
        }
        if (!"BOOKED".equals(a.getStatus())) {
            throw new ApiException(ErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "A " + a.getStatus().toLowerCase() + " appointment cannot be cancelled");
        }
        Clinic clinic = clinics.findById(a.getClinicId()).orElseThrow();
        Instant cutoff = a.getSlotStart()
                .minus(Duration.ofHours(clinic.getCancellationCutoffHours()));
        if (!cutoff.isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.CANCELLATION_WINDOW_PASSED,
                    "This appointment can no longer be cancelled",
                    Map.of("cancellationCutoffHours",
                            String.valueOf(clinic.getCancellationCutoffHours())));
        }
        a.cancel();
        return AppointmentView.of(appointments.save(a));
    }

    // ---------- internals ----------

    private Appointment ownAppointment(CarepointPrincipal principal, UUID id) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        return appointments.findById(id)
                .filter(a -> a.getPatientId().equals(principal.userId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Not found"));
    }

    private Optional<AppointmentView> checkKey(UUID key, UUID userId, String requestHash) {
        return idempotencyKeys.findById(key).flatMap(stored -> {
            if (stored.getCreatedAt().isBefore(Instant.now().minus(KEY_TTL))) {
                idempotencyKeys.deleteById(key);
                idempotencyKeys.flush();
                return Optional.empty();
            }
            if (!stored.getUserId().equals(userId) || !stored.getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This idempotency key was used with a different request");
            }
            try {
                return Optional.of(mapper.readValue(stored.getResponse(), AppointmentView.class));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private AppointmentView bookAndStore(Clinic clinic, UUID doctorId, UUID patientId,
                                         Instant start, Instant end, String reason,
                                         UUID key, String requestHash) {
        // Re-validate against the engine — availability display is advisory;
        // the booking path always checks for real (docs/09 §2.3).
        LocalDate day = LocalDate.ofInstant(start, ZoneId.of(clinic.getTimezone()));
        boolean validSlot = availability.available(clinic.getId(), doctorId, day, day)
                .stream()
                .anyMatch(s -> s.start().equals(start) && s.end().equals(end));
        if (!validSlot) {
            throw new ApiException(ErrorCode.INVALID_SLOT,
                    "That time is not a bookable slot for this doctor");
        }

        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO appointments
                      (id, clinic_id, doctor_id, patient_id, slot, reason, created_by)
                    VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?, ?)
                    """,
                    id, clinic.getId(), doctorId, patientId,
                    OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(end, ZoneOffset.UTC),
                    reason, patientId);
        } catch (DataAccessException e) {
            // exclusion-constraint violation or deadlock loser — same meaning
            throw new ApiException(ErrorCode.SLOT_TAKEN,
                    "That slot was just taken — pick another");
        }

        Appointment a = appointments.findById(id).orElseThrow();
        AppointmentView view = AppointmentView.of(a);
        if (key != null) {
            try {
                idempotencyKeys.save(new IdempotencyKey(key, patientId,
                        requestHash, mapper.writeValueAsString(view)));
            } catch (Exception e) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not persist request");
            }
        }
        return view;
    }

    private String hash(Object... parts) {
        try {
            String raw = String.join("|",
                    java.util.Arrays.stream(parts)
                            .map(p -> String.valueOf(p))
                            .toList());
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
