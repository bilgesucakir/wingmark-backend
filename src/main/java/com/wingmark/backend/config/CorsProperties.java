package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Browser origins allowed to call the API cross-origin. Empty by default: nobody needs it today. */
@ConfigurationProperties(prefix = "wingmark.cors")
public record CorsProperties(@DefaultValue({}) List<String> allowedOrigins) {
}
