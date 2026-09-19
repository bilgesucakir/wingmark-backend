package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** baseUrl is this backend's own public URL, used to build links (e.g. email verification) that point back at it. */
@ConfigurationProperties(prefix = "wingmark.app")
public record AppProperties(String baseUrl) {
}
