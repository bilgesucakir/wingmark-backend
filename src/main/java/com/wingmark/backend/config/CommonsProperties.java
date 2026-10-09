package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for reading licence and author details from Wikimedia Commons (a public API, no key). Commons blocks
 * anonymous clients, so every request carries a descriptive User-Agent with a contact address.
 *
 * @param baseUrl   Commons web address
 * @param userAgent identifies this app and how to reach us
 */
@ConfigurationProperties(prefix = "wingmark.commons")
public record CommonsProperties(
        @DefaultValue("https://commons.wikimedia.org") String baseUrl,
        @DefaultValue("Wingmark/1.0 (support@wingmarkapp.com)") String userAgent
) {
}
