package com.wingmark.backend.config;

import com.wingmark.backend.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Audit trail of every change an admin makes (method, path, outcome, admin id). Ids only - no
 * request bodies or other personal data. Runs after Spring Security, so the principal and its
 * database-sourced role are already known.
 */
@Slf4j
@Component
public class AdminAuditLogFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !MUTATING.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal && principal.isAdmin()) {
                log.info("ADMIN_AUDIT admin={} {} {} -> {}",
                        principal.getId(), request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        }
    }
}
