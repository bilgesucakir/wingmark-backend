package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Current versions (and public URLs) of the Terms of Service and Privacy Policy. While a
 * version is blank the document isn't published yet, so acceptance isn't required. Once it
 * is set, signup must accept exactly that version and existing users are asked to accept it.
 */
@ConfigurationProperties(prefix = "wingmark.legal")
public record LegalProperties(String termsVersion, String termsUrl, String privacyVersion, String privacyUrl) {

    public boolean termsPublished() {
        return StringUtils.hasText(termsVersion);
    }

    public boolean privacyPublished() {
        return StringUtils.hasText(privacyVersion);
    }
}
