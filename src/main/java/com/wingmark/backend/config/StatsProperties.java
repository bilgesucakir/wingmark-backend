package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the anonymous usage statistics.
 *
 * @param minGroupSize fewest different users a species, region or bucket needs before it is shown
 */
@ConfigurationProperties(prefix = "wingmark.stats")
public record StatsProperties(@DefaultValue("5") int minGroupSize) {
}
