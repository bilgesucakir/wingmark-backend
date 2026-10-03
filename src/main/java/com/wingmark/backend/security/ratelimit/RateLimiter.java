package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counters held in memory. That's correct for the single Render instance this
 * runs on; with several instances each would count separately (effective limit x N), at
 * which point this should move to a shared store such as Redis.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final RateLimitProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    /** Counts one request against the key; throws 429 once more than max happen within the window. */
    public void check(String key, int max, Duration window) {
        if (!properties.enabled()) {
            return;
        }
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.expiresAt <= now);
        }
        Window current = windows.compute(key.toLowerCase(Locale.ROOT), (k, w) ->
                w == null || w.expiresAt <= now ? new Window(now + window.toMillis(), 1) : w.increment());
        if (current.count > max) {
            throw new RateLimitExceededException(Math.max(1, (current.expiresAt - now + 999) / 1000));
        }
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record Window(long expiresAt, int count) {
        Window increment() {
            return new Window(expiresAt, count + 1);
        }
    }
}
