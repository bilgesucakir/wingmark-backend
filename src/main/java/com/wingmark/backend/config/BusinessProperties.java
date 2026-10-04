package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Operator details shown in the email footer; empty means no footer.
 *
 * @param legalName    operator name; the footer is shown only when this is set
 * @param address      postal address (optional)
 * @param contactEmail support address, also named in the "this wasn't me" emails
 */
@ConfigurationProperties(prefix = "wingmark.business")
public record BusinessProperties(String legalName, String address, String contactEmail) {

    /** Whether the footer is shown, i.e. a legal name is set. */
    public boolean isConfigured() {
        return StringUtils.hasText(legalName);
    }
}
