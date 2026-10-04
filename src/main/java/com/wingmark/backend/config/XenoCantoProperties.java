package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the Xeno-canto API client (optional API key). */
@ConfigurationProperties(prefix = "wingmark.xeno-canto")
public record XenoCantoProperties(String baseUrl, String apiKey) {
}
