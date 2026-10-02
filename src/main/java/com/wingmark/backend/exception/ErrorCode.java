package com.wingmark.backend.exception;

/**
 * Stable, machine-readable error identifiers returned as ErrorResponse.code, so clients can
 * branch on a specific failure (e.g. show the verify-email screen) without parsing the
 * human-readable message. Values are part of the API contract: add new ones freely, but
 * never rename or repurpose an existing one.
 */
public enum ErrorCode {
    // 400
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INVALID_PARAMETER,
    BAD_REQUEST,
    OBSERVED_AT_IN_FUTURE,
    SAME_PASSWORD,
    INVALID_PROFILE_PICTURE,
    INVALID_BOUNDS,
    INVALID_OR_EXPIRED_CODE,
    INVALID_FILE,
    TERMS_NOT_ACCEPTED,
    PRIVACY_NOT_ACCEPTED,
    CONSENT_VERSION_MISMATCH,
    // 401
    UNAUTHENTICATED,
    INVALID_CREDENTIALS,
    INVALID_OR_EXPIRED_TOKEN,
    // 403
    FORBIDDEN,
    EMAIL_NOT_VERIFIED,
    WRONG_PASSWORD,
    CANNOT_MODIFY_SELF,
    // 404 / 405 / 406
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    // 409
    CONFLICT,
    EMAIL_TAKEN,
    USERNAME_TAKEN,
    LAST_ADMIN,
    // 413 / 415 / 422
    FILE_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    INVALID_REFERENCE,
    // 5xx
    EXTERNAL_SERVICE_ERROR,
    INTERNAL_ERROR
}
