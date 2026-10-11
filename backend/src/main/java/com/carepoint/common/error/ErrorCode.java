package com.carepoint.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes returned in the API error envelope.
 * Codes are part of the API contract — add, never rename.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),

    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),

    EMAIL_TAKEN(HttpStatus.CONFLICT),
    SLOT_TAKEN(HttpStatus.CONFLICT),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
