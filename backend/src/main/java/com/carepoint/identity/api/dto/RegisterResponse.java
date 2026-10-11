package com.carepoint.identity.api.dto;

import java.util.UUID;

public record RegisterResponse(UUID id, String email, String message) {
}
