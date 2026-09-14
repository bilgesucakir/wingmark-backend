package com.wingmark.backend.exception;

/** Thrown when a downstream third-party call (e.g. Xeno-canto) fails or times out. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
