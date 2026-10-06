package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private static RateLimitProperties props(boolean enabled) {
        return new RateLimitProperties(enabled, 600, 10, 50, 5, 3, 20, 10, 30, 120, 10, 20);
    }

    @Test
    void allowsUpToTheLimitThenThrows429WithRetryAfterAndResetsWhenTheWindowEnds() {
        RateLimiter limiter = new RateLimiter(props(true));
        Instant start = Instant.parse("2026-10-03T10:00:00Z");
        limiter.setClock(Clock.fixed(start, ZoneOffset.UTC));

        for (int i = 0; i < 3; i++) {
            limiter.check("login:acct:a@b.co", 3, Duration.ofMinutes(15));
        }
        assertThatThrownBy(() -> limiter.check("login:acct:a@b.co", 3, Duration.ofMinutes(15)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(900));
        // Keys are case-insensitive, so "A@B.CO" can't dodge the limit.
        assertThatThrownBy(() -> limiter.check("login:acct:A@B.CO", 3, Duration.ofMinutes(15)))
                .isInstanceOf(RateLimitExceededException.class);
        // Another account is unaffected.
        assertThatCode(() -> limiter.check("login:acct:other@b.co", 3, Duration.ofMinutes(15))).doesNotThrowAnyException();

        limiter.setClock(Clock.fixed(start.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
        assertThatCode(() -> limiter.check("login:acct:a@b.co", 3, Duration.ofMinutes(15))).doesNotThrowAnyException();
    }

    @Test
    void disabledLimiterNeverThrows() {
        RateLimiter limiter = new RateLimiter(props(false));
        for (int i = 0; i < 100; i++) {
            limiter.check("k", 1, Duration.ofMinutes(1));
        }
    }

    @Test
    void clientIpPrefersCloudflareThenForwardedForThenSocket() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        assertThat(ClientIp.of(request)).isEqualTo("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2");
        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.7");
        request.addHeader("CF-Connecting-IP", "198.51.100.9");
        assertThat(ClientIp.of(request)).isEqualTo("198.51.100.9");
    }
}
