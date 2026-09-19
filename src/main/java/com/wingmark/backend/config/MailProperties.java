package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wingmark.mail")
public record MailProperties(String from) {
}
