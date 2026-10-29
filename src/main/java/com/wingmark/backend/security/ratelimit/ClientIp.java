package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.config.ClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Resolves the caller's address for the rate limits from the one source set in {@link ClientIpProperties}.
 * Headers from other sources are ignored, so a caller cannot choose its own rate-limit key; a value that is not an
 * IP address falls back to the socket address.
 */
@Component
@RequiredArgsConstructor
public class ClientIp {

    /** Hex digits, dots, colons and an optional zone: all an IPv4 or IPv6 literal can contain. */
    private static final Pattern LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final ClientIpProperties properties;

    public String of(HttpServletRequest request) {
        String candidate = switch (properties.source()) {
            case CLOUDFLARE -> request.getHeader("CF-Connecting-IP");
            case FORWARDED_FOR -> fromForwardedFor(request.getHeader("X-Forwarded-For"));
            case REMOTE_ADDR -> null;
        };
        return isAddress(candidate) ? candidate.trim() : request.getRemoteAddr();
    }

    /** The entry added by the outermost trusted proxy: {@code trustedProxyHops} places from the right. */
    private String fromForwardedFor(String header) {
        if (!StringUtils.hasText(header) || properties.trustedProxyHops() < 1) {
            return null;
        }
        String[] entries = header.split(",");
        int index = Math.max(0, entries.length - properties.trustedProxyHops());
        return entries[index].trim();
    }

    private static boolean isAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (!LITERAL.matcher(trimmed).matches()) {
            return false; // also keeps the host name lookup below away from arbitrary text
        }
        try {
            InetAddress.getByName(trimmed); // a literal: no DNS query
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
