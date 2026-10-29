package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cap on how many login password checks (bcrypt, which is CPU-heavy) run at the same time, so a burst of logins
 * cannot starve the rest of the API. Over the cap the login is answered with 429 and a Retry-After header.
 *
 * @param maxConcurrent     checks allowed at once; 0 or less turns the cap off
 * @param waitMillis        how long a login may wait for a free slot before it is turned away
 * @param retryAfterSeconds the Retry-After value sent with the 429
 */
@ConfigurationProperties(prefix = "wingmark.bcrypt")
public record BcryptLimitProperties(
        @DefaultValue("4") int maxConcurrent,
        @DefaultValue("500") long waitMillis,
        @DefaultValue("2") long retryAfterSeconds
) {
}
