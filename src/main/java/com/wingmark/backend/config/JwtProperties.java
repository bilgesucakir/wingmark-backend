package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wingmark.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMs,
        long refreshTokenExpirationMs
) {
}
