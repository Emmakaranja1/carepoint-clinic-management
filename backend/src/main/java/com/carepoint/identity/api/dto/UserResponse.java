package com.carepoint.identity.api.dto;

import java.util.Map;
import java.util.UUID;

import com.carepoint.identity.domain.User;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        boolean passwordSet,
        Map<UUID, String> clinicRoles) {

    public static UserResponse of(User user, Map<UUID, String> clinicRoles) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getPasswordHash() != null, clinicRoles);
    }
}
