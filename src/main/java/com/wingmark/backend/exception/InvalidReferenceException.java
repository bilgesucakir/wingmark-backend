package com.wingmark.backend.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a request body references a resource that does not exist; maps to 422. */
public class InvalidReferenceException extends ApiException {

    public InvalidReferenceException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INVALID_REFERENCE, message);
    }

    public static InvalidReferenceException of(String field, String resource, Object identifier) {
        return new InvalidReferenceException(field + " refers to a " + resource + " that does not exist: " + identifier);
    }
}
