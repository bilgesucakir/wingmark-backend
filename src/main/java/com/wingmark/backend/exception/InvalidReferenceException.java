package com.wingmark.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request body points at another resource that doesn't exist (e.g. a bird
 * log's speciesId). Distinct from ResourceNotFoundException: the URL itself was found, the
 * payload just can't be processed, so this maps to 422 rather than 404.
 */
public class InvalidReferenceException extends ApiException {

    public InvalidReferenceException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INVALID_REFERENCE, message);
    }

    public static InvalidReferenceException of(String field, String resource, Object identifier) {
        return new InvalidReferenceException(field + " refers to a " + resource + " that does not exist: " + identifier);
    }
}
