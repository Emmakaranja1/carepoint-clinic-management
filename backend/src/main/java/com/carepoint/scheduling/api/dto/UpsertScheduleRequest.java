package com.carepoint.scheduling.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.carepoint.scheduling.application.ScheduleService;

public record UpsertScheduleRequest(
        @NotNull @Valid List<Window> windows) {

    public List<ScheduleService.WindowInput> toInputs() {
        return windows.stream()
                .map(w -> new ScheduleService.WindowInput(w.dayOfWeek(), w.startTime(),
                        w.endTime(), w.slotMinutes(), w.validFrom(), w.validTo()))
                .toList();
    }

    public record Window(
            @NotNull Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull Integer slotMinutes,
            LocalDate validFrom,
            LocalDate validTo) {
    }
}
