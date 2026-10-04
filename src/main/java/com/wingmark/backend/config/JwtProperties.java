package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings.
 *
 * @param secret                  signing key
 * @param accessTokenExpirationMs access token lifetime in milliseconds
 * @param refreshTokenExpirationMs refresh token lifetime in milliseconds
 */
@ConfigurationProperties(prefix = "wingmark.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMs,
        long refreshTokenExpirationMs
) {
}
