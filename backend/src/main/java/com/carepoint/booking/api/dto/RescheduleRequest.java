package com.carepoint.booking.api.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RescheduleRequest(
        UUID doctorId,
        @NotNull Instant start,
        @NotNull Instant end,
        @Size(max = 500) String reason) {
}
