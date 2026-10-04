package com.wingmark.backend.exception;

/** Thrown when login credentials or a password check are wrong. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
