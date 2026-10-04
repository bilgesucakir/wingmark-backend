package com.wingmark.backend.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/** Resolves the client IP from {@code CF-Connecting-IP}, then the first {@code X-Forwarded-For} entry, then the socket address. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String cloudflare = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cloudflare)) {
            return cloudflare.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
