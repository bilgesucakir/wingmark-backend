package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "wingmark.pwned-passwords")
public record PwnedPasswordsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://api.pwnedpasswords.com") String baseUrl
) {
}
