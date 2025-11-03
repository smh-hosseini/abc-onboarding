package com.abcbank.onboarding.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT Authentication Entry Point
 * Handles authentication failures and returns RFC 7807 Problem Details
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_BASE_URL = "https://api.abc.nl/errors/";

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        String traceId = UUID.randomUUID().toString();
        String requestUri = request.getRequestURI();

        log.warn("Authentication failed for request: {} {} - traceId: {}, reason: {}",
                request.getMethod(), requestUri, traceId, authException.getMessage());

        // Create RFC 7807 Problem Detail
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Authentication required. Please provide a valid JWT token in the Authorization header."
        );
        problemDetail.setType(URI.create(ERROR_BASE_URL + "unauthorized"));
        problemDetail.setTitle("Unauthorized");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setProperty("path", requestUri);

        // Set response status and content type
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        // Write JSON response
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
