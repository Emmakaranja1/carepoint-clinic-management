package com.carepoint.common.security;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticated-user snapshot attached to the security context.
 * Memberships are loaded fresh per request — clinic roles never go stale
 * inside a long-lived token.
 */
public record CarepointPrincipal(
        UUID userId,
        String email,
        String status,
        Map<UUID, String> clinicRoles) {

    public Optional<String> roleIn(UUID clinicId) {
        return Optional.ofNullable(clinicRoles.get(clinicId));
    }

    public boolean isPatient() {
        return clinicRoles.isEmpty();
    }
}
