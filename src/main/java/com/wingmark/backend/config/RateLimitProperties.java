package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate limits, each as maximum requests per window. Account limits key on email or user id, IP limits on the
 * client address. All have defaults and can be overridden.
 *
 * @param enabled                       master switch
 * @param globalPerIpPerMinute          all requests per IP
 * @param loginPerAccountPer15Min       logins per account
 * @param loginPerIpPer15Min            logins per IP
 * @param registerPerIpPerHour          signups per IP
 * @param resetRequestPerAccountPerHour reset-code requests per account
 * @param resetRequestPerIpPerHour      reset-code requests per IP
 * @param resetConfirmPerAccountPer15Min reset confirmations per account
 * @param resetConfirmPerIpPer15Min     reset confirmations per IP
 * @param refreshPerIpPer15Min          token refreshes per IP
 * @param passwordCheckPerUserPer15Min  password confirmations (change, delete) per user
 * @param registerPerIpPerDay           signups per IP per day, on top of the hourly limit
 */
@ConfigurationProperties(prefix = "wingmark.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("600") int globalPerIpPerMinute,
        @DefaultValue("10") int loginPerAccountPer15Min,
        @DefaultValue("50") int loginPerIpPer15Min,
        @DefaultValue("5") int registerPerIpPerHour,
        @DefaultValue("3") int resetRequestPerAccountPerHour,
        @DefaultValue("20") int resetRequestPerIpPerHour,
        @DefaultValue("10") int resetConfirmPerAccountPer15Min,
        @DefaultValue("30") int resetConfirmPerIpPer15Min,
        @DefaultValue("120") int refreshPerIpPer15Min,
        @DefaultValue("10") int passwordCheckPerUserPer15Min,
        @DefaultValue("20") int registerPerIpPerDay
) {
}
