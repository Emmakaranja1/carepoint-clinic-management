package com.carepoint.booking.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.booking.api.dto.BookAppointmentRequest;
import com.carepoint.booking.application.BookingService;
import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;
import com.carepoint.common.security.CarepointPrincipal;

/** Patient booking endpoints (docs/01 US-B1..B3). */
@RestController
@RequestMapping("/api/v1/appointments")
public class BookingController {

    private final BookingService booking;

    public BookingController(BookingService booking) {
        this.booking = booking;
    }

    @PostMapping
    public ResponseEntity<BookingService.AppointmentView> book(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemHeader,
            @Valid @RequestBody BookAppointmentRequest req) {
        UUID key = null;
        if (idemHeader != null && !idemHeader.isBlank()) {
            try {
                key = UUID.fromString(idemHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Request validation failed",
                        java.util.Map.of("Idempotency-Key", "Must be a UUID"));
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(booking.book(principal, req.clinicId(), req.doctorId(),
                        req.start(), req.end(), req.reason(), key));
    }

    @GetMapping("/mine")
    public List<BookingService.AppointmentView> mine(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @RequestParam(defaultValue = "upcoming") String when) {
        return booking.mine(principal, when);
    }

    @GetMapping("/{id}")
    public BookingService.AppointmentView get(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID id) {
        return booking.get(principal, id);
    }

    @PostMapping("/{id}/cancel")
    public BookingService.AppointmentView cancel(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID id) {
        return booking.cancel(principal, id);
    }
}
