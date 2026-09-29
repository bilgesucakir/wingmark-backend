package com.wingmark.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** An error that already knows its HTTP status and ErrorCode, so the handler maps it as-is. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
