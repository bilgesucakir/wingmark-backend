package com.wingmark.backend.security;

import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private Claims claims;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenPopulatesTheSecurityContext() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, Role.ADMIN, true)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void roleComesFromTheDatabaseNotTheTokenClaims() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseClaims("demoted-admin-token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        // The token was issued while the user was an admin; they've since been demoted.
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, Role.USER, true)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer demoted-admin-token");
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void tokenForADeletedUserIsNotAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseClaims("orphan-token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer orphan-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tokenForAnUnverifiedUserIsNotAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseClaims("unverified-token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, Role.USER, false)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer unverified-token");
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenMintedBeforeTheUsersLatestSessionResetIsNotAuthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        UUID userId = UUID.randomUUID();
        User user = user(userId, Role.USER, true);
        user.setTokenVersion(2); // e.g. password changed twice since this token was issued
        when(jwtTokenProvider.parseClaims("stale-token")).thenReturn(claims);
        when(jwtTokenProvider.getUserId(claims)).thenReturn(userId);
        when(jwtTokenProvider.getTokenVersion(claims)).thenReturn(1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer stale-token");
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static User user(UUID id, Role role, boolean emailVerified) {
        User user = User.builder().email("someone@example.com").role(role).emailVerified(emailVerified).build();
        user.setId(id);
        return user;
    }

    @Test
    void invalidTokenClearsTheSecurityContextAndStillContinuesTheChain() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        when(jwtTokenProvider.parseClaims("bad-token")).thenThrow(new JwtException("invalid"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void requestWithNoAuthorizationHeaderIsPassedThroughUnauthenticated() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonBearerAuthorizationHeaderIsIgnored() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
