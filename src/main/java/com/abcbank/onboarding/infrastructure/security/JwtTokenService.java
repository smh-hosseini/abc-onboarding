package com.abcbank.onboarding.infrastructure.security;

import com.abcbank.onboarding.domain.model.User;
import com.abcbank.onboarding.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * JWT Token Service for generating and validating JWT tokens
 * Supports different token types for APPLICANT, COMPLIANCE_OFFICER, and ADMIN
 */
@Slf4j
@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long applicantTokenExpiry;
    private final long officerTokenExpiry;
    private final long adminTokenExpiry;
    private final long refreshTokenExpiry;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(
            @Value("${jwt.secret:default-secret-key-change-in-production-min-32-chars}") String secret,
            @Value("${jwt.expiry.applicant:900000}") long applicantExpiry,      // 15 minutes
            @Value("${jwt.expiry.officer:1800000}") long officerExpiry,         // 30 minutes
            @Value("${jwt.expiry.admin:600000}") long adminExpiry,              // 10 minutes
            @Value("${jwt.expiry.refresh:2592000000}") long refreshExpiry       // 30 days
    ) {
        // Ensure secret is at least 32 characters for HS256
        if (secret.length() < 32) {
            log.warn("JWT secret is too short. Using padded secret. CHANGE THIS IN PRODUCTION!");
            secret = secret + "0".repeat(32 - secret.length());
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.applicantTokenExpiry = applicantExpiry;
        this.officerTokenExpiry = officerExpiry;
        this.adminTokenExpiry = adminExpiry;
        this.refreshTokenExpiry = refreshExpiry;

        log.info("JWT Token Service initialized with expiry times - Applicant: {}ms, Officer: {}ms, Admin: {}ms, Refresh: {}ms",
                applicantExpiry, officerExpiry, adminExpiry, refreshExpiry);
    }

    /**
     * Generate JWT token for APPLICANT after OTP verification
     */
    public String generateApplicantToken(UUID applicationId) {
        log.debug("Generating APPLICANT token for application: {}", applicationId);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "applicant_session");
        claims.put("application_id", applicationId.toString());
        claims.put("roles", new String[]{"APPLICANT"});

        String token = Jwts.builder()
                .claims(claims)
                .subject("app-" + applicationId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(applicantTokenExpiry)))
                .signWith(signingKey)
                .compact();

        log.info("Generated APPLICANT token for application: {}", applicationId);
        return token;
    }

    /**
     * Generate access token for internal user (COMPLIANCE_OFFICER or ADMIN)
     * This is a unified method that generates tokens based on user role
     */
    public String generateAccessToken(User user, String sessionId) {
        log.debug("Generating access token for user: {}", user.getUsername());

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "employee");
        claims.put("session_id", sessionId);
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("roles", new String[]{user.getRole().name()});

        // Determine expiry based on role
        long expiry = user.getRole() == UserRole.ADMIN ? adminTokenExpiry : officerTokenExpiry;

        String token = Jwts.builder()
                .claims(claims)
                .subject("user-" + user.getId().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(expiry)))
                .signWith(signingKey)
                .compact();

        log.info("Generated {} access token for user: {}", user.getRole(), user.getUsername());
        return token;
    }

    public String generateOfficerToken(UUID userId, String employeeId, String email, String sessionId) {
        log.debug("Generating COMPLIANCE_OFFICER token for user: {}", userId);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "employee");
        claims.put("session_id", sessionId);
        claims.put("employee_id", employeeId);
        claims.put("email", email);
        claims.put("roles", new String[]{"COMPLIANCE_OFFICER"});

        String token = Jwts.builder()
                .claims(claims)
                .subject("user-" + userId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(officerTokenExpiry)))
                .signWith(signingKey)
                .compact();

        log.info("Generated COMPLIANCE_OFFICER token for employee: {}", employeeId);
        return token;
    }

    public String generateAdminToken(UUID userId, String employeeId, String email, String sessionId) {
        log.debug("Generating ADMIN token for user: {}", userId);

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "employee");
        claims.put("session_id", sessionId);
        claims.put("employee_id", employeeId);
        claims.put("email", email);
        claims.put("roles", new String[]{"ADMIN"});

        String token = Jwts.builder()
                .claims(claims)
                .subject("user-" + userId.toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(adminTokenExpiry)))
                .signWith(signingKey)
                .compact();

        log.info("Generated ADMIN token for employee: {}", employeeId);
        return token;
    }

    /**
     * Generate refresh token (random secure token)
     * Returns both the plain token and its SHA-256 hash
     */
    public RefreshTokenPair generateRefreshToken() {
        log.debug("Generating refresh token");

        // Generate 32-byte random token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Generate SHA-256 hash for storage
        String tokenHash = hashToken(token);

        log.info("Generated refresh token");
        return new RefreshTokenPair(token, tokenHash);
    }

    /**
     * Hash a token using SHA-256
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Get refresh token expiry timestamp
     */
    public LocalDateTime getRefreshTokenExpiry() {
        return LocalDateTime.now().plusSeconds(refreshTokenExpiry / 1000);
    }

    /**
     * Get refresh token expiry in milliseconds
     */
    public long getRefreshTokenExpiryMillis() {
        return refreshTokenExpiry;
    }

    /**
     * Validate JWT token and extract claims
     * @return Claims if valid, null if invalid
     */
    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            return null;
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            log.error("Invalid token: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract application ID from APPLICANT token
     */
    public UUID extractApplicationId(Claims claims) {
        String applicationId = claims.get("application_id", String.class);
        if (applicationId == null) {
            log.error("No application_id found in token claims");
            return null;
        }
        try {
            return UUID.fromString(applicationId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid application_id format: {}", applicationId);
            return null;
        }
    }

    /**
     * Extract user ID from employee token
     */
    public UUID extractUserId(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || !subject.startsWith("user-")) {
            log.error("Invalid subject format: {}", subject);
            return null;
        }
        try {
            return UUID.fromString(subject.substring(5)); // Remove "user-" prefix
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format: {}", subject);
            return null;
        }
    }

    /**
     * Extract roles from token
     */
    @SuppressWarnings("unchecked")
    public String[] extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof String[]) {
            return (String[]) roles;
        } else if (roles instanceof java.util.List) {
            java.util.List<String> roleList = (java.util.List<String>) roles;
            return roleList.toArray(new String[0]);
        }
        log.warn("No roles found in token claims");
        return new String[0];
    }

    /**
     * Extract session ID from employee token
     */
    public String extractSessionId(Claims claims) {
        return claims.get("session_id", String.class);
    }

    /**
     * Extract employee ID from employee token
     * @deprecated Use extractUsername instead
     */
    @Deprecated
    public String extractEmployeeId(Claims claims) {
        return claims.get("employee_id", String.class);
    }

    /**
     * Extract username from employee token
     */
    public String extractUsername(Claims claims) {
        // Try new format first, fall back to old format
        String username = claims.get("username", String.class);
        if (username == null) {
            username = claims.get("employee_id", String.class);
        }
        return username;
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        boolean expired = expiration.before(new Date());
        if (expired) {
            log.debug("Token expired at: {}", expiration);
        }
        return expired;
    }

    /**
     * Get remaining validity time in milliseconds
     */
    public long getRemainingValidity(Claims claims) {
        Date expiration = claims.getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Record holding both the plain refresh token and its hash.
     * The plain token is sent to the client, the hash is stored in the database.
     */
    public record RefreshTokenPair(String token, String tokenHash) {}
}
