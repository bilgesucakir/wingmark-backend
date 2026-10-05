package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Published versions and URLs of the Terms of Service and Privacy Policy. A blank version means the
 * document is unpublished and not required at signup; a set version must be accepted by new and existing users.
 *
 * @param termsVersion   current terms version
 * @param termsUrl       public terms URL
 * @param privacyVersion current privacy policy version
 * @param privacyUrl     public privacy policy URL
 * @param minimumAge     minimum age to sign up; users confirm it at signup and the age is the consent version
 */
@ConfigurationProperties(prefix = "wingmark.legal")
public record LegalProperties(String termsVersion, String termsUrl, String privacyVersion, String privacyUrl,
                              @DefaultValue("13") int minimumAge) {

    /** Whether a terms version is set. */
    public boolean termsPublished() {
        return StringUtils.hasText(termsVersion);
    }

    /** Whether a privacy policy version is set. */
    public boolean privacyPublished() {
        return StringUtils.hasText(privacyVersion);
    }
}
