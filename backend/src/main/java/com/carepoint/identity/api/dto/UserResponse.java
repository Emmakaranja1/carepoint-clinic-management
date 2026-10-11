package com.carepoint.identity.api.dto;

import java.util.Map;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Map<UUID, String> clinicRoles) {
}
