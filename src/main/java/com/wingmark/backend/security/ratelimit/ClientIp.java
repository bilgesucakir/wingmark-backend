package com.wingmark.backend.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * The caller's address behind Render's Cloudflare edge. CF-Connecting-IP is set (and
 * overwritten) by Cloudflare, so a client can't forge it on traffic that passes through it;
 * X-Forwarded-For's first entry is the fallback, then the socket address.
 */
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
