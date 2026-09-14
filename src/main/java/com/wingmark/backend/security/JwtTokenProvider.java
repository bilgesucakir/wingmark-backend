package com.wingmark.backend.security;

import com.wingmark.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
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

    private final JwtProperties jwtProperties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
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
