package com.wingmark.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects non-upload request bodies over 1 MB with 413 before they are parsed. */
@Component
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_JSON_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;

    /** Multipart uploads are skipped; they have their own limit. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_JSON_BYTES) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(
                    HttpStatus.PAYLOAD_TOO_LARGE.value(), HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                    ErrorCode.REQUEST_TOO_LARGE, "Request body exceeds 1 MB", request.getRequestURI())));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
