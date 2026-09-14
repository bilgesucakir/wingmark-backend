package com.wingmark.backend.exception;

/** Thrown when an authenticated user attempts an action they don't have permission for
 *  (e.g. accessing another user's log, or a non-admin hitting an admin-only endpoint). */
public class UnauthorizedActionException extends RuntimeException {

    public UnauthorizedActionException(String message) {
        super(message);
    }
}
