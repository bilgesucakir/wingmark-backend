package com.wingmark.backend.exception;

/** Thrown when a unique value, such as an email, is already taken. */
public class DuplicateResourceException extends RuntimeException {

    private final ErrorCode code;

    public DuplicateResourceException(String message) {
        this(ErrorCode.CONFLICT, message);
    }

    public DuplicateResourceException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
