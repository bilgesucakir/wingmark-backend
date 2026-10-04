package com.wingmark.backend.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wingmark.backend.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminAuditLogFilterTest {

    private final AdminAuditLogFilter filter = new AdminAuditLogFilter();
    private final FilterChain chain = mock(FilterChain.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(AdminAuditLogFilter.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(logs);
        SecurityContextHolder.clearContext();
    }

    private void signInAs(String authority, UUID id) {
        UserPrincipal principal = new UserPrincipal(id, "a@b.com", "hash", List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void onlyApiWritesAreAudited() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/users"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("DELETE", "/admin/app.js"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("DELETE", "/api/users/1"))).isFalse();
    }

    @Test
    void anAdminWriteIsLoggedWithTheAdminIdMethodPathAndStatus() throws Exception {
        UUID adminId = UUID.randomUUID();
        signInAs("ROLE_ADMIN", adminId);

        filter.doFilter(new MockHttpServletRequest("DELETE", "/api/users/42"), new MockHttpServletResponse(), chain);

        assertThat(logs.list).singleElement().satisfies(e -> assertThat(e.getFormattedMessage())
                .contains("ADMIN_AUDIT", adminId.toString(), "DELETE", "/api/users/42", "200"));
    }

    @Test
    void nonAdminWritesAreNotLogged() throws Exception {
        signInAs("ROLE_USER", UUID.randomUUID());

        filter.doFilter(new MockHttpServletRequest("POST", "/api/bird-logs"), new MockHttpServletResponse(), chain);

        assertThat(logs.list).isEmpty();
    }
}
