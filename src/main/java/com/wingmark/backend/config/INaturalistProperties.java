package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the iNaturalist API client. */
@ConfigurationProperties(prefix = "wingmark.inaturalist")
public record INaturalistProperties(String baseUrl) {
}
