package com.carepoint.clinic.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationTokenRequest(@NotBlank @Size(max = 128) String token) {
}
