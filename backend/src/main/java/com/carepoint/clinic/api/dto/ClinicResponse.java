package com.carepoint.clinic.api.dto;

import java.util.UUID;

public record ClinicResponse(UUID id, String name, String slug,
                             String timezone, String message) {
}
