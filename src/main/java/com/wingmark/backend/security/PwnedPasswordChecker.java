package com.wingmark.backend.security;

import com.wingmark.backend.config.PwnedPasswordsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Checks a password against Have I Been Pwned using k-anonymity (only the first 5 SHA-1 characters leave the
 * server).
 * Fails open when the API is unavailable.
 */
@Slf4j
@Component
public class PwnedPasswordChecker {

    private final PwnedPasswordsProperties properties;
    private final WebClient webClient;

    @Autowired
    public PwnedPasswordChecker(PwnedPasswordsProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    PwnedPasswordChecker(PwnedPasswordsProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    /** True only when the password is confirmed to appear in a known breach. */
    public boolean isBreached(String password) {
        if (!properties.enabled()) {
            return false;
        }
        String hash = sha1Hex(password);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);
        try {
            String body = webClient.get()
                    .uri("/range/{prefix}", prefix)
                    // Padding makes every response a similar size, hiding even the match count.
                    .header("Add-Padding", "true")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
            if (body == null) {
                return false;
            }
            return body.lines().anyMatch(line -> {
                int colon = line.indexOf(':');
                return colon > 0
                        && line.substring(0, colon).equalsIgnoreCase(suffix)
                        && !line.substring(colon + 1).trim().equals("0"); // padding entries have count 0
            });
        } catch (Exception e) {
            log.warn("Pwned Passwords check unavailable - allowing the password ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
