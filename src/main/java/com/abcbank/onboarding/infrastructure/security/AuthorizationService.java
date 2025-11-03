package com.abcbank.onboarding.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Authorization Service for fine-grained access control
 * Used in @PreAuthorize annotations for method-level security
 */
@Slf4j
@Service("authorizationService")
public class AuthorizationService {

    /**
     * Check if the authenticated user is the owner of the application
     * Used for APPLICANT role to ensure they can only access their own application
     */
    public boolean isOwner(UUID applicationId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authentication found");
            return false;
        }

        // Extract application ID from JWT claims
        if (authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
            JwtAuthenticationFilter.JwtAuthenticationDetails details =
                    (JwtAuthenticationFilter.JwtAuthenticationDetails) authentication.getDetails();

            String tokenApplicationId = details.getApplicationId();
            if (tokenApplicationId == null) {
                log.warn("No application_id found in JWT token for user: {}", authentication.getName());
                return false;
            }

            try {
                UUID tokenAppUuid = UUID.fromString(tokenApplicationId);
                boolean isOwner = tokenAppUuid.equals(applicationId);

                if (!isOwner) {
                    log.warn("Access denied - User {} attempted to access application {} but owns {}",
                            authentication.getName(), applicationId, tokenAppUuid);
                }

                return isOwner;
            } catch (IllegalArgumentException e) {
                log.error("Invalid application_id format in token: {}", tokenApplicationId);
                return false;
            }
        }

        log.warn("Authentication details are not JwtAuthenticationDetails");
        return false;
    }

    /**
     * Check if the user has a specific role
     */
    public boolean hasRole(String role, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean hasRole = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));

        log.debug("User {} has role {}: {}", authentication.getName(), role, hasRole);
        return hasRole;
    }

    /**
     * Check if the user is a compliance officer or admin
     */
    public boolean isStaff(Authentication authentication) {
        return hasRole("COMPLIANCE_OFFICER", authentication) || hasRole("ADMIN", authentication);
    }

    /**
     * Extract employee ID from authentication
     */
    public String getEmployeeId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
            JwtAuthenticationFilter.JwtAuthenticationDetails details =
                    (JwtAuthenticationFilter.JwtAuthenticationDetails) authentication.getDetails();
            return details.getEmployeeId();
        }

        return null;
    }

    /**
     * Extract application ID from authentication (for APPLICANT)
     */
    public UUID getApplicationId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails) {
            JwtAuthenticationFilter.JwtAuthenticationDetails details =
                    (JwtAuthenticationFilter.JwtAuthenticationDetails) authentication.getDetails();

            String appId = details.getApplicationId();
            if (appId != null) {
                try {
                    return UUID.fromString(appId);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid application_id format: {}", appId);
                }
            }
        }

        return null;
    }
}
