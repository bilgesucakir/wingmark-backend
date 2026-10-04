package com.wingmark.backend.exception;

/** Thrown when an authenticated user is not allowed to do something; maps to 403. */
public class UnauthorizedActionException extends RuntimeException {

    private final ErrorCode code;

    public UnauthorizedActionException(String message) {
        this(ErrorCode.FORBIDDEN, message);
    }

    public UnauthorizedActionException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
