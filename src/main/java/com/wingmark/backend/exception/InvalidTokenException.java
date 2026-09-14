package com.wingmark.backend.exception;

/** Thrown for expired/revoked/malformed refresh tokens or password-reset tokens. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
