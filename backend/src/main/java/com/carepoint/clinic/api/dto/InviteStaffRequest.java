package com.carepoint.clinic.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteStaffRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank String role,
        @Size(max = 30) String phone) {
}
