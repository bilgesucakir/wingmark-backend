package com.wingmark.backend.security;

import com.wingmark.backend.config.BcryptLimitProperties;
import com.wingmark.backend.security.ratelimit.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Limits how many password checks run at once. A check waits briefly for a free slot; if none frees up in time the
 * caller gets a 429 with Retry-After (the iOS app already handles that), not a 503.
 */
@Component
public class BcryptGate {

    private final BcryptLimitProperties properties;
    private final Semaphore slots;

    public BcryptGate(BcryptLimitProperties properties) {
        this.properties = properties;
        this.slots = properties.maxConcurrent() > 0 ? new Semaphore(properties.maxConcurrent(), true) : null;
    }

    /** Runs the check once a slot is free; throws {@link RateLimitExceededException} when none is within the wait. */
    public <T> T run(Supplier<T> check) {
        if (slots == null) {
            return check.get();
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(properties.waitMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RateLimitExceededException(properties.retryAfterSeconds());
        }
        if (!acquired) {
            throw new RateLimitExceededException(properties.retryAfterSeconds());
        }
        try {
            return check.get();
        } finally {
            slots.release();
        }
    }
}
