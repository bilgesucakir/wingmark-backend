package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * The operator's legal details, shown in email footers. Deliberately empty by default:
 * these are facts only the business owner can supply, so nothing is shown until they're
 * configured (never a made-up or placeholder value in a real email).
 */
@ConfigurationProperties(prefix = "wingmark.business")
public record BusinessProperties(String legalName, String address, String contactEmail) {

    public boolean isConfigured() {
        return StringUtils.hasText(legalName);
    }
}
