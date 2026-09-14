package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wingmark.xeno-canto")
public record XenoCantoProperties(String baseUrl) {
}
