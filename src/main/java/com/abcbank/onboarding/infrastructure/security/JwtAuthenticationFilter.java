package com.abcbank.onboarding.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Authentication Filter
 * Extracts JWT from Authorization header and sets SecurityContext
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        log.debug("Processing request: {} {}", request.getMethod(), requestUri);

        // Skip JWT validation for public endpoints
        if (isPublicEndpoint(requestUri)) {
            log.debug("Public endpoint, skipping JWT validation: {}", requestUri);
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT from Authorization header
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            log.debug("No Bearer token found in Authorization header");
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            // Validate token and extract claims
            Claims claims = jwtTokenService.validateToken(jwt);
            if (claims == null) {
                log.warn("Invalid or expired JWT token");
                filterChain.doFilter(request, response);
                return;
            }

            // Check if token is expired
            if (jwtTokenService.isTokenExpired(claims)) {
                log.warn("JWT token has expired");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract roles and create authorities
            String[] roles = jwtTokenService.extractRoles(claims);
            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

            // Create authentication token
            String subject = claims.getSubject();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(subject, null, authorities);

            // Add additional details (application ID, session ID, etc.)
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Store claims in authentication details for later use
            JwtAuthenticationDetails details = new JwtAuthenticationDetails(claims);
            authentication.setDetails(details);

            // Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Successfully authenticated user: {} with roles: {}", subject, Arrays.toString(roles));

        } catch (Exception e) {
            log.error("Error processing JWT token: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if endpoint is public (no authentication required)
     */
    private boolean isPublicEndpoint(String uri) {
        return uri.startsWith("/api/v1/onboarding/applications") &&
               (uri.endsWith("/send-otp") ||
                uri.endsWith("/verify-otp") ||
                uri.endsWith("/status") ||
                uri.matches("/api/v1/onboarding/applications/?$")) ||
               uri.startsWith("/swagger-ui") ||
               uri.startsWith("/v3/api-docs") ||
               uri.startsWith("/actuator/health") ||
               uri.startsWith("/actuator/info");
    }

    /**
     * Custom authentication details to store JWT claims
     */
    public static class JwtAuthenticationDetails {
        private final Claims claims;

        public JwtAuthenticationDetails(Claims claims) {
            this.claims = claims;
        }

        public Claims getClaims() {
            return claims;
        }

        public String getApplicationId() {
            return claims.get("application_id", String.class);
        }

        public String getSessionId() {
            return claims.get("session_id", String.class);
        }

        public String getEmployeeId() {
            return claims.get("employee_id", String.class);
        }

        public String getEmail() {
            return claims.get("email", String.class);
        }

        public String getType() {
            return claims.get("type", String.class);
        }
    }
}
