package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Per-account and per-IP limits for credential endpoints. Every attempt counts and the limits do not reveal whether an email exists. */
@Component
@RequiredArgsConstructor
public class AuthRateLimits {

    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final RateLimiter rateLimiter;
    private final RateLimitProperties limits;

    public void login(HttpServletRequest request, String email) {
        rateLimiter.check("login:ip:" + ClientIp.of(request), limits.loginPerIpPer15Min(), FIFTEEN_MINUTES);
        rateLimiter.check("login:acct:" + email, limits.loginPerAccountPer15Min(), FIFTEEN_MINUTES);
    }

    public void register(HttpServletRequest request) {
        rateLimiter.check("register:ip:" + ClientIp.of(request), limits.registerPerIpPerHour(), ONE_HOUR);
    }

    /** Forgot-password and resend-verification: both send an email, so both are capped per address. */
    public void emailSending(HttpServletRequest request, String kind, String email) {
        rateLimiter.check(kind + ":ip:" + ClientIp.of(request), limits.resetRequestPerIpPerHour(), ONE_HOUR);
        rateLimiter.check(kind + ":acct:" + email, limits.resetRequestPerAccountPerHour(), ONE_HOUR);
    }

    public void resetConfirm(HttpServletRequest request, String email) {
        rateLimiter.check("reset-confirm:ip:" + ClientIp.of(request), limits.resetConfirmPerIpPer15Min(), FIFTEEN_MINUTES);
        rateLimiter.check("reset-confirm:acct:" + email, limits.resetConfirmPerAccountPer15Min(), FIFTEEN_MINUTES);
    }

    public void refresh(HttpServletRequest request) {
        rateLimiter.check("refresh:ip:" + ClientIp.of(request), limits.refreshPerIpPer15Min(), FIFTEEN_MINUTES);
    }

    /** Endpoints that re-check the current password (change password, delete account). */
    public void passwordCheck(UUID userId) {
        rateLimiter.check("password-check:user:" + userId, limits.passwordCheckPerUserPer15Min(), FIFTEEN_MINUTES);
    }
}
