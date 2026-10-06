package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitsRegisterTest {

    /** A clock the test can move. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-06T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final RateLimitProperties limits = new RateLimitProperties(true, 600, 10, 50, 5, 3, 20, 10, 30, 120, 10, 20);
    private final RateLimiter limiter = new RateLimiter(limits);
    private final AuthRateLimits authRateLimits = new AuthRateLimits(limiter, limits);

    private static MockHttpServletRequest from(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", ip);
        return request;
    }

    AuthRateLimitsRegisterTest() {
        limiter.setClock(clock);
    }

    @Test
    void anAddressMayRegisterFiveTimesAnHourAndThenIsBlocked() {
        for (int i = 0; i < 5; i++) {
            authRateLimits.register(from("203.0.113.1"));
        }

        assertThatThrownBy(() -> authRateLimits.register(from("203.0.113.1"))).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> authRateLimits.register(from("203.0.113.2"))).doesNotThrowAnyException();
    }

    @Test
    void theHourlyLimitResetsButTheDailyLimitOfTwentyStillHolds() {
        for (int hour = 0; hour < 4; hour++) {
            for (int i = 0; i < 5; i++) {
                authRateLimits.register(from("203.0.113.9"));
            }
            clock.advance(Duration.ofMinutes(61));
        }

        // 20 signups so far today: the next one is refused although the hourly window is fresh.
        assertThatThrownBy(() -> authRateLimits.register(from("203.0.113.9"))).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofHours(24));
        assertThatCode(() -> authRateLimits.register(from("203.0.113.9"))).doesNotThrowAnyException();
    }
}
