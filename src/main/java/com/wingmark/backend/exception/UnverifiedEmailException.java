package com.wingmark.backend.exception;

/** Thrown when an account's email is not verified yet. */
public class UnverifiedEmailException extends RuntimeException {

    public UnverifiedEmailException(String message) {
        super(message);
    }
}
