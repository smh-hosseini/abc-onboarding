package com.abcbank.onboarding.infrastructure.security;

import com.abcbank.onboarding.infrastructure.exception.RateLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Rate Limiting Filter
 * Applies rate limits to API endpoints based on IP, application, resource
 * Order 0: Runs first, before JWT and Session filters
 */
@Slf4j
@Component
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ERROR_BASE_URL = "https://api.abc.nl/errors/";

    // Rate limit configurations (from application.yml)
    private static final int CREATE_APPLICATION_LIMIT = 5;
    private static final long CREATE_APPLICATION_WINDOW = 3600000; // 1 hour

    private static final int SEND_OTP_IP_LIMIT = 3;
    private static final long SEND_OTP_WINDOW = 3600000; // 1 hour

    private static final int VERIFY_OTP_APP_LIMIT = 3;
    private static final int VERIFY_OTP_IP_LIMIT = 10;
    private static final long VERIFY_OTP_WINDOW = 3600000; // 1 hour

    private static final int UPLOAD_DOCUMENT_LIMIT = 10;
    private static final long UPLOAD_DOCUMENT_WINDOW = 3600000; // 1 hour

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        String ipAddress = getClientIpAddress(request);

        try {
            // Apply rate limits based on endpoint
            if (method.equals("POST") && requestUri.matches("/api/v1/onboarding/applications/?")) {
                // Create application - IP based
                applyRateLimit(ipAddress, "create_application",
                        CREATE_APPLICATION_LIMIT, CREATE_APPLICATION_WINDOW, response);

            } else if (method.equals("POST") && requestUri.matches(".*/send-otp")) {
                // Send OTP - IP based
                applyRateLimit(ipAddress, "send_otp",
                        SEND_OTP_IP_LIMIT, SEND_OTP_WINDOW, response);

            } else if (method.equals("POST") && requestUri.matches(".*/verify-otp")) {
                // Verify OTP - IP and application based
                UUID applicationId = extractApplicationId(requestUri);
                if (applicationId != null) {
                    rateLimitService.checkApplicationRateLimit(applicationId, "verify_otp_app",
                            VERIFY_OTP_APP_LIMIT, VERIFY_OTP_WINDOW);
                }
                applyRateLimit(ipAddress, "verify_otp_ip",
                        VERIFY_OTP_IP_LIMIT, VERIFY_OTP_WINDOW, response);

            } else if (method.equals("POST") && requestUri.contains("/documents")) {
                // Upload document - Application based
                UUID applicationId = extractApplicationId(requestUri);
                if (applicationId != null) {
                    rateLimitService.checkApplicationRateLimit(applicationId, "upload_document",
                            UPLOAD_DOCUMENT_LIMIT, UPLOAD_DOCUMENT_WINDOW);
                }
                addRateLimitHeaders(response, ipAddress, "upload_document", UPLOAD_DOCUMENT_LIMIT);
            }

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (RateLimitExceededException e) {
            handleRateLimitExceeded(request, response, e);
        }
    }

    /**
     * Apply rate limit and add headers
     */
    private void applyRateLimit(String key, String resource, int limit, long windowMillis,
                                 HttpServletResponse response) {
        rateLimitService.checkRateLimit(key, resource, limit, windowMillis);
        addRateLimitHeaders(response, key, resource, limit);
    }

    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, String key,
                                      String resource, int limit) {
        try {
            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(key, resource, limit);
            response.setHeader("X-RateLimit-Limit", String.valueOf(info.getLimit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(info.getRemaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(info.getResetTimestamp()));
        } catch (Exception e) {
            log.debug("Could not add rate limit headers", e);
        }
    }

    /**
     * Handle rate limit exceeded - return 429 with RFC 7807 Problem Detail
     */
    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response,
                                          RateLimitExceededException e) throws IOException {
        String traceId = UUID.randomUUID().toString();
        String ipAddress = getClientIpAddress(request);

        log.warn("Rate limit exceeded for IP: {} on endpoint: {} - traceId: {}",
                maskIp(ipAddress), request.getRequestURI(), traceId);

        // Create RFC 7807 Problem Detail
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                e.getMessage()
        );
        problemDetail.setType(URI.create(ERROR_BASE_URL + "rate-limit"));
        problemDetail.setTitle("Rate Limit Exceeded");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setProperty("path", request.getRequestURI());

        // Set response status and headers
        response.setStatus(429); // 429 Too Many Requests
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", "3600"); // 1 hour

        // Write JSON response
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Extract application ID from URI
     */
    private UUID extractApplicationId(String uri) {
        try {
            String[] parts = uri.split("/");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("applications") && i + 1 < parts.length) {
                    return UUID.fromString(parts[i + 1]);
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug("Could not extract application ID from URI: {}", uri);
        }
        return null;
    }

    /**
     * Mask IP for logging (GDPR)
     */
    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        return ip.substring(0, Math.min(4, ip.length())) + "****";
    }
}
