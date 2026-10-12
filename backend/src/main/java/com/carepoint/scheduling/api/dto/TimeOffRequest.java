package com.carepoint.scheduling.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TimeOffRequest(
        @NotNull Instant offFrom,
        @NotNull Instant offTo,
        @Size(max = 500) String reason) {
}
