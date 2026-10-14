package com.carepoint.booking.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.booking.api.dto.DeskBookRequest;
import com.carepoint.booking.api.dto.RescheduleRequest;
import com.carepoint.booking.api.dto.TransitionRequest;
import com.carepoint.booking.application.BookingService.AppointmentView;
import com.carepoint.booking.application.DeskService;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;

/** Receptionist desk + doctor agenda (docs/01 §5.6, US-S3). */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}")
public class DeskController {

    private final DeskService desk;

    public DeskController(DeskService desk) {
        this.desk = desk;
    }

    @GetMapping("/appointments")
    public DeskService.AgendaPage agenda(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return desk.agenda(principal, clinicId, from, to, doctorId, status, page, size);
    }

    @GetMapping("/me/appointments")
    public List<DeskService.AgendaItem> myAgenda(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return desk.myAgenda(principal, clinicId, date);
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentView> bookOnBehalf(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemHeader,
            @Valid @RequestBody DeskBookRequest req) {
        DeskBookRequest.InlinePatient p = req.patient();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(desk.bookOnBehalf(principal, clinicId, req.doctorId(),
                        req.start(), req.end(), req.reason(), req.patientId(),
                        p == null ? null : p.email(),
                        p == null ? null : p.fullName(),
                        p == null ? null : p.phone(),
                        parseIdempotencyKey(idemHeader)));
    }

    private static UUID parseIdempotencyKey(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Request validation failed",
                    java.util.Map.of("Idempotency-Key", "Must be a UUID"));
        }
    }

    @PatchMapping("/appointments/{id}")
    public AppointmentView transition(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID id,
            @Valid @RequestBody TransitionRequest req) {
        return desk.transition(principal, clinicId, id, req.status());
    }

    @PostMapping("/appointments/{id}/reschedule")
    public ResponseEntity<AppointmentView> reschedule(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(desk.reschedule(principal, clinicId, id, req.doctorId(),
                        req.start(), req.end(), req.reason()));
    }
}
