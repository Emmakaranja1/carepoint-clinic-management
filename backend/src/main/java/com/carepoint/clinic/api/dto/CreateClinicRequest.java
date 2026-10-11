package com.carepoint.clinic.api.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClinicRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank String timezone,
        Map<String, Object> address) {
}
