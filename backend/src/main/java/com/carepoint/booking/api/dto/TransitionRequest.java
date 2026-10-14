package com.carepoint.booking.api.dto;

import jakarta.validation.constraints.NotNull;

public record TransitionRequest(@NotNull String status) {
}
