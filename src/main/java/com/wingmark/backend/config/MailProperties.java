package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Outgoing mail settings; {@code from} is the sender address. */
@ConfigurationProperties(prefix = "wingmark.mail")
public record MailProperties(String from) {
}
