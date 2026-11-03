package com.carepoint.clinic.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(max = 72) String password,
        @NotBlank @Size(max = 72) String confirmPassword) {
}
