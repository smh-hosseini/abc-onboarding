package com.abcbank.onboarding.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Session validation filter for COMPLIANCE_OFFICER and ADMIN users
 * Validates Redis session after JWT authentication
 * Order 2: Runs after JwtAuthenticationFilter (Order 1)
 */
@Slf4j
@Component
@Order(2)
public class SessionFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final JwtTokenService jwtTokenService;

    public SessionFilter(SessionService sessionService, JwtTokenService jwtTokenService) {
        this.sessionService = sessionService;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Get authentication from SecurityContext (set by JwtAuthenticationFilter)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            // Check if this is an employee (officer/admin) - they need session validation
            if (authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
                JwtAuthenticationFilter.JwtAuthenticationDetails details =
                        (JwtAuthenticationFilter.JwtAuthenticationDetails) authentication.getDetails();

                String sessionId = details.getSessionId();
                String tokenType = details.getType();

                // Only validate sessions for employee tokens (not applicant tokens)
                if ("employee".equals(tokenType) && sessionId != null) {
                    String currentIp = getClientIpAddress(request);
                    String currentUserAgent = request.getHeader("User-Agent");

                    // Validate session
                    Session session = sessionService.validateSession(sessionId, currentIp, currentUserAgent);

                    if (session == null) {
                        log.warn("Invalid or expired session: {} for user: {}",
                                sessionId, authentication.getName());
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Session expired or invalid\",\"sessionId\":\"" + sessionId + "\"}");
                        return;
                    }

                    log.debug("Session validated successfully: {} for user: {}",
                            sessionId, session.getEmployeeId());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract client IP address from request
     * Handles X-Forwarded-For header for proxy/load balancer scenarios
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, first one is the client
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
