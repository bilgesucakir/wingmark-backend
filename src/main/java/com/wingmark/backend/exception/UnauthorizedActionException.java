package com.wingmark.backend.exception;

/** Thrown when an authenticated user attempts an action they don't have permission for
 *  (e.g. accessing another user's log, or a non-admin hitting an admin-only endpoint). */
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
