package com.carepoint.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 30)
        @Pattern(regexp = "^$|^\\+[1-9]\\d{6,14}$", message = "Must be an international number, e.g. +254712345678")
        String phone) {
}
