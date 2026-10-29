package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the rate limits read the caller's address from. A header is only as trustworthy as the proxy that sets
 * it, so exactly one source is trusted and the others are ignored.
 *
 * @param source          {@code CLOUDFLARE}: the {@code CF-Connecting-IP} header (Render serves the API through
 *                        Cloudflare, which sets it and rejects a client-supplied one); {@code FORWARDED_FOR}: the
 *                        {@code X-Forwarded-For} entry added by the last trusted proxy; {@code REMOTE_ADDR}: the
 *                        socket address, for an app that is reached directly
 * @param trustedProxyHops with {@code FORWARDED_FOR}: how many proxies you run in front of the app, counted from
 *                        the right of the header (each one appends the address it saw); entries to the left of
 *                        those are written by the caller and never trusted
 */
@ConfigurationProperties(prefix = "wingmark.client-ip")
public record ClientIpProperties(
        @DefaultValue("CLOUDFLARE") Source source,
        @DefaultValue("1") int trustedProxyHops
) {

    /** The trusted source of the client address. */
    public enum Source {
        CLOUDFLARE, FORWARDED_FOR, REMOTE_ADDR
    }
}
