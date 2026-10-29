package com.wingmark.backend.exception;

import java.util.Map;

/** A 400 VALIDATION_FAILED for a value outside the request body (header, parameter), with the usual validationErrors map. */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, String> validationErrors;

    public ValidationFailedException(Map<String, String> validationErrors) {
        super("Validation failed");
        this.validationErrors = validationErrors;
    }

    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}
