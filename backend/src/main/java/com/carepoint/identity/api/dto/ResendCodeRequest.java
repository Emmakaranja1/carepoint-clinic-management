package com.carepoint.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResendCodeRequest(
        @NotBlank @Email String email,
        @Pattern(regexp = "VERIFY_EMAIL|PASSWORD_RESET") String purpose) {
}
