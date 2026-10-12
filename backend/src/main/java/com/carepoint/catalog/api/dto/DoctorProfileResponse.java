package com.carepoint.catalog.api.dto;

import java.util.UUID;

public record DoctorProfileResponse(UUID id, UUID membershipId, UUID specialtyId,
                                    String bio, Integer consultMinutes) {
}
