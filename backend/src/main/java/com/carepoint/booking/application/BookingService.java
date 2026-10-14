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
import java.util.function.Supplier;

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

    public record AppointmentView(UUID id, UUID clinicId, UUID doctorId, UUID patientId,
                                  String status, Instant start, Instant end,
                                  String reason, boolean bookedForGuest,
                                  Instant cancelledAt) {
        static AppointmentView of(Appointment a) {
            return new AppointmentView(a.getId(), a.getClinicId(), a.getDoctorId(),
                    a.getPatientId(), a.getStatus(), a.getSlotStart(), a.getSlotEnd(),
                    a.getReason(), a.isBookedForGuest(), a.getCancelledAt());
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

        UUID pid = patient.getId();
        String requestHash = idempotencyKey == null ? null
                : requestHash(clinicId, doctorId, start, end, reason);
        return withIdempotency(idempotencyKey, pid, requestHash,
                () -> insertValidated(clinic, doctorId, pid, pid, start, end,
                        reason, false));
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

    /**
     * Idempotency wrapper shared by patient booking and desk book-on-behalf:
     * same key + same request → the stored response replays; same key +
     * different request → 409. A null key just runs the write. Must run
     * inside the caller's transaction — key row and business write commit
     * or roll back together.
     */
    public AppointmentView withIdempotency(UUID key, UUID userId, String requestHash,
                                           Supplier<AppointmentView> write) {
        if (key == null) {
            return write.get();
        }
        Optional<AppointmentView> replay = checkKey(key, userId, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        AppointmentView view = write.get();
        try {
            idempotencyKeys.save(new IdempotencyKey(key, userId,
                    requestHash, mapper.writeValueAsString(view)));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not persist request");
        }
        return view;
    }

    /** Request fingerprint — include every field that defines the write. */
    public static String requestHash(Object... parts) {
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

    /**
     * Shared write path for patient self-booking and desk book-on-behalf:
     * re-validates the slot against the availability engine, then inserts —
     * the partial exclusion constraint arbitrates concurrent writers.
     * Must run inside a transaction.
     */
    public AppointmentView insertValidated(Clinic clinic, UUID doctorId, UUID patientId,
                                           UUID createdBy, Instant start, Instant end,
                                           String reason, boolean bookedForGuest) {
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
                      (id, clinic_id, doctor_id, patient_id, slot, reason,
                       created_by, booked_for_guest)
                    VALUES (?, ?, ?, ?, tstzrange(?, ?, '[)'), ?, ?, ?)
                    """,
                    id, clinic.getId(), doctorId, patientId,
                    OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(end, ZoneOffset.UTC),
                    reason, createdBy, bookedForGuest);
        } catch (DataAccessException e) {
            // exclusion-constraint violation or deadlock loser — same meaning
            throw new ApiException(ErrorCode.SLOT_TAKEN,
                    "That slot was just taken — pick another");
        }
        return AppointmentView.of(appointments.findById(id).orElseThrow());
    }
}
