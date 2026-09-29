package com.wingmark.backend.security;

import com.wingmark.backend.entity.User;
import com.wingmark.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                UUID userId = jwtTokenProvider.getUserId(claims);

                // Checked against the database on every request rather than trusting the
                // token's claims, so a deleted or un-verified account loses access at once and
                // role changes apply immediately, instead of lingering until the token expires.
                // Tokens issued before a password change/reset or logout-all are rejected too.
                int tokenVersion = jwtTokenProvider.getTokenVersion(claims);
                Optional<User> user = userRepository.findById(userId)
                        .filter(User::isEmailVerified)
                        .filter(u -> tokenVersion >= u.currentTokenVersion());
                if (user.isPresent()) {
                    UserPrincipal principal = new UserPrincipal(
                            userId,
                            user.get().getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name()))
                    );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
