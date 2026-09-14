package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wingmark.inaturalist")
public record INaturalistProperties(String baseUrl) {
}
