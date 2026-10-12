package com.carepoint.catalog.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateDoctorProfileRequest(
        UUID specialtyId,
        @Size(max = 2000) String bio,
        @Positive Integer consultMinutes) {
}
