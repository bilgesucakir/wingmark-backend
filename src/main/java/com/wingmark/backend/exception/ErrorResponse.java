package com.wingmark.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** JSON error body returned by the API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        Map<String, String> validationErrors
) {

    public static ErrorResponse of(int status, String error, ErrorCode code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, null);
    }

    public static ErrorResponse ofValidation(int status, String error, String message, String path,
                                              Map<String, String> validationErrors) {
        return new ErrorResponse(Instant.now(), status, error, ErrorCode.VALIDATION_FAILED, message, path, validationErrors);
    }
}
