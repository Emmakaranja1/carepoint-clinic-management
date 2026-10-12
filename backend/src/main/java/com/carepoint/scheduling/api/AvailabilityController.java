package com.carepoint.scheduling.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.scheduling.application.AvailabilityService;

/**
 * Public availability read (docs/09 §2.3). Rate-limited per-IP in
 * SecurityConfig (60/min) to blunt scraping.
 */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/doctors/{doctorId}/availability")
public class AvailabilityController {

    private final AvailabilityService availability;

    public AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    public List<AvailabilityService.Slot> availability(
            @PathVariable UUID clinicId,
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availability.available(clinicId, doctorId, from, to);
    }
}
