package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Public base URL of this backend, used for links in emails. */
@ConfigurationProperties(prefix = "wingmark.app")
public record AppProperties(String baseUrl) {
}
