package com.wingmark.backend.security;

import com.wingmark.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-key-must-be-long-enough-1234567890";

    private final JwtProperties properties = new JwtProperties(SECRET, 900_000L, 2_592_000_000L);
    private final JwtTokenProvider provider = new JwtTokenProvider(properties);

    @Test
    void generatedTokenRoundTripsUserIdAndRole() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "someone@example.com", "USER");

        Claims claims = provider.parseClaims(token);

        assertThat(provider.getUserId(claims)).isEqualTo(userId);
        assertThat(provider.getRole(claims)).isEqualTo("USER");
        assertThat(claims.get("email", String.class)).isEqualTo("someone@example.com");
    }

    @Test
    void parseClaimsRejectsTokenSignedWithADifferentKey() {
        SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-signing-key-1234567890".getBytes(StandardCharsets.UTF_8));
        String tokenSignedElsewhere = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> provider.parseClaims(tokenSignedElsewhere))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaimsRejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "USER")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> provider.parseClaims(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseClaimsRejectsMalformedToken() {
        assertThatThrownBy(() -> provider.parseClaims("not-a-real-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void exposesConfiguredExpirations() {
        assertThat(provider.getAccessTokenExpirationMs()).isEqualTo(900_000L);
        assertThat(provider.getRefreshTokenExpirationMs()).isEqualTo(2_592_000_000L);
    }
}
