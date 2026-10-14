package com.carepoint.reporting.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carepoint.common.security.CarepointPrincipal;
import com.carepoint.reporting.application.ReportService;

/** Operational aggregates (docs/01 US-P1) — staff only, clinic-scoped. */
@RestController
@RequestMapping("/api/v1/clinics/{clinicId}/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public ReportService.SummaryReport summary(
            @AuthenticationPrincipal CarepointPrincipal principal,
            @PathVariable UUID clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.summary(principal, clinicId, from, to);
    }
}
