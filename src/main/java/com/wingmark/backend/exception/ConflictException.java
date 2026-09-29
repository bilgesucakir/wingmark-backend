package com.wingmark.backend.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a request is valid but conflicts with the current state (e.g. removing the last admin). */
public class ConflictException extends ApiException {

    public ConflictException(ErrorCode code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
