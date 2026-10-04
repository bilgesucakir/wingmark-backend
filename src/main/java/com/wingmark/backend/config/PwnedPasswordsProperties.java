package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Have I Been Pwned password check.
 *
 * @param enabled whether passwords are checked against known breaches
 * @param baseUrl API base URL
 */
@ConfigurationProperties(prefix = "wingmark.pwned-passwords")
public record PwnedPasswordsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.pwnedpasswords.com") String baseUrl
) {
}
