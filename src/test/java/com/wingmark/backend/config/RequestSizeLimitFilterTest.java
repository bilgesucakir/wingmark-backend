package com.wingmark.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new ObjectMapper().findAndRegisterModules());
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletRequest request(int bytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContent(new byte[bytes]);
        return request;
    }

    @Test
    void bodiesOverOneMegabyteAreRejectedWith413AndAJsonError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(1024 * 1024 + 1), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        verifyNoInteractions(chain);
    }

    @Test
    void bodiesUpToOneMegabytePassThrough() throws Exception {
        MockHttpServletRequest request = request(1024 * 1024);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void multipartUploadsAreNotCheckedHere() {
        MockHttpServletRequest request = request(5);
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=x");

        assertThat(filter.shouldNotFilter(request)).isTrue();
        assertThat(filter.shouldNotFilter(request(5))).isFalse();
    }
}
