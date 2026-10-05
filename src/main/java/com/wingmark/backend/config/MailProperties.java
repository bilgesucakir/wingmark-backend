package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Outgoing mail settings.
 *
 * @param from     sender address
 * @param dailyCap most emails sent per UTC day; further ones are skipped with a warning. 0 means no cap.
 */
@ConfigurationProperties(prefix = "wingmark.mail")
public record MailProperties(String from, @DefaultValue("200") int dailyCap) {
}
