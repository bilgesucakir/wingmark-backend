package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Request limits. Each limit is "max requests per window"; per-account limits key on the
 * email or user id so spreading attempts across IPs doesn't help, per-IP limits key on the
 * client address. Defaults suit a single small instance; all can be overridden.
 */
@ConfigurationProperties(prefix = "wingmark.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("600") int globalPerIpPerMinute,
        @DefaultValue("10") int loginPerAccountPer15Min,
        @DefaultValue("50") int loginPerIpPer15Min,
        @DefaultValue("10") int registerPerIpPerHour,
        @DefaultValue("3") int resetRequestPerAccountPerHour,
        @DefaultValue("20") int resetRequestPerIpPerHour,
        @DefaultValue("10") int resetConfirmPerAccountPer15Min,
        @DefaultValue("30") int resetConfirmPerIpPer15Min,
        @DefaultValue("120") int refreshPerIpPer15Min,
        @DefaultValue("10") int passwordCheckPerUserPer15Min
) {
}
