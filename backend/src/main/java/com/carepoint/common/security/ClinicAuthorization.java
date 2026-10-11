package com.carepoint.common.security;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.carepoint.common.error.ApiException;
import com.carepoint.common.error.ErrorCode;

/**
 * Clinic-scoped authorization, per the authz matrix (docs/04 §4):
 *   * user with no staff memberships anywhere        → 403 ACCESS_DENIED
 *   * staff of a different clinic                    → 404 NOT_FOUND
 *     (the clinic's existence is never leaked)
 *   * member of the clinic but missing the role      → 403 ACCESS_DENIED
 * Called from service layer so mutations and reads share one rule.
 */
@Component
public class ClinicAuthorization {

    public String requireRole(CarepointPrincipal principal, UUID clinicId, String... allowedRoles) {
        String role = membershipRole(principal, clinicId);
        if (allowedRoles.length > 0 && Arrays.stream(allowedRoles).noneMatch(role::equals)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access denied");
        }
        return role;
    }

    public String requireMembership(CarepointPrincipal principal, UUID clinicId) {
        return membershipRole(principal, clinicId);
    }

    private String membershipRole(CarepointPrincipal principal, UUID clinicId) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        if (principal.isPatient()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Access denied");
        }
        String role = principal.clinicRoles().get(clinicId);
        if (role == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Not found");
        }
        return role;
    }
}
