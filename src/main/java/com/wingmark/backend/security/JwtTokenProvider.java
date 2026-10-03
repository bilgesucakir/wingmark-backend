package com.wingmark.backend.security;

import com.wingmark.backend.config.AppProperties;
import com.wingmark.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final JwtProperties jwtProperties;
    private final AppProperties appProperties;

    /** The fallback in application.yml, for local development only. */
    static final String DEV_SECRET = "change-this-development-only-secret-key-please-override-me-1234567890";

    /**
     * Refuses to start a deployed server on the public development secret: anyone could read
     * it in the repo and forge an admin token. Local runs (APP_BASE_URL on localhost) may use it.
     */
    @PostConstruct
    void rejectDevelopmentSecretOutsideLocalhost() {
        boolean local = appProperties.baseUrl() == null
                || appProperties.baseUrl().contains("localhost") || appProperties.baseUrl().contains("127.0.0.1");
        if (DEV_SECRET.equals(jwtProperties.secret()) && !local) {
            throw new IllegalStateException("JWT_SECRET is not set: refusing to start a deployed server ("
                    + appProperties.baseUrl() + ") with the public development secret");
        }
        if (jwtProperties.secret() == null || jwtProperties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, String role, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtProperties.accessTokenExpirationMs())))
                .signWith(key())
                .compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("Invalid or expired token", ex);
        }
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /** The user's tokenVersion when this token was minted; 0 for tokens that predate the claim. */
    public int getTokenVersion(Claims claims) {
        Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        return version == null ? 0 : version;
    }

    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public long getRefreshTokenExpirationMs() {
        return jwtProperties.refreshTokenExpirationMs();
    }

    public long getAccessTokenExpirationMs() {
        return jwtProperties.accessTokenExpirationMs();
    }
}
