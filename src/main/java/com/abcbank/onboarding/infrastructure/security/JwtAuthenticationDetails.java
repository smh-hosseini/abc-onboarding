package com.abcbank.onboarding.infrastructure.security;

import io.jsonwebtoken.Claims;
import lombok.Getter;

import java.util.UUID;

/**
 * Custom authentication details that stores JWT claims for authorization checks.
 * Used to store additional information beyond username and roles.
 */
@Getter
public class JwtAuthenticationDetails {

    private final Claims claims;

    public JwtAuthenticationDetails(Claims claims) {
        this.claims = claims;
    }

    /**
     * Extract application ID from APPLICANT token claims.
     * Returns null for non-applicant tokens.
     */
    public UUID getApplicationId() {
        String applicationId = claims.get("application_id", String.class);
        if (applicationId == null) {
            return null;
        }
        try {
            return UUID.fromString(applicationId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get token type (applicant_session, employee, etc.)
     */
    public String getTokenType() {
        return claims.get("type", String.class);
    }

    /**
     * Get session ID (for employee tokens)
     */
    public String getSessionId() {
        return claims.get("session_id", String.class);
    }
}
