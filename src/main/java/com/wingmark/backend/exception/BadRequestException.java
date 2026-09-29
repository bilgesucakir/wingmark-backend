package com.wingmark.backend.exception;

import org.springframework.http.HttpStatus;

/** A 400 for a well-formed request whose values are unacceptable (e.g. a future observedAt). */
public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
