package com.wingmark.backend.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.config.RateLimitProperties;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** A generous per-IP ceiling on the whole API, so no single client can flood it. */
@Component
@RequiredArgsConstructor
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties limits;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            rateLimiter.check("global:ip:" + ClientIp.of(request), limits.globalPerIpPerMinute(), Duration.ofMinutes(1));
        } catch (RateLimitExceededException ex) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(), HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    ErrorCode.RATE_LIMITED, ex.getMessage(), request.getRequestURI())));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
