package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 429 with the number of seconds until the caller may try again (sent as Retry-After). */
@Getter
public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                "Too many attempts - please try again in " + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
