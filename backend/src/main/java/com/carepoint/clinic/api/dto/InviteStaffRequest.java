package com.carepoint.clinic.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InviteStaffRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank String role,
        @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$", message = "Use international format, e.g. +254712345678")
        String phone) {
}
