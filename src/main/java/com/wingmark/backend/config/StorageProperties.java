package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wingmark.storage")
public record StorageProperties(String uploadDir) {
}
