package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.ApiResponseDto;
import com.abcbank.onboarding.adapter.in.web.dto.CurrentSessionResponse;
import com.abcbank.onboarding.adapter.in.web.dto.SessionRefreshResponse;
import com.abcbank.onboarding.adapter.in.web.dto.SessionCountResponse;
import com.abcbank.onboarding.infrastructure.security.JwtAuthenticationFilter;
import com.abcbank.onboarding.infrastructure.security.JwtTokenService;
import com.abcbank.onboarding.infrastructure.security.Session;
import com.abcbank.onboarding.infrastructure.security.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Session Management Controller
 * Handles session operations for COMPLIANCE_OFFICER and ADMIN users
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/session")
@Tag(name = "Session Management", description = "Session management for officers and admins")
@SecurityRequirement(name = "bearer-jwt")
public class SessionController {

    private final SessionService sessionService;
    private final JwtTokenService jwtTokenService;

    public SessionController(SessionService sessionService, JwtTokenService jwtTokenService) {
        this.sessionService = sessionService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Get current session information
     */
    @GetMapping("/current")
    @Operation(summary = "Get current session", description = "Retrieve current session information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session information retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or expired session")
    })
    public ResponseEntity<?> getCurrentSession() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails details) {

                String sessionId = details.getSessionId();
                if (sessionId != null) {
                    CurrentSessionResponse sessionInfo = new CurrentSessionResponse(
                            sessionId,
                            details.getEmployeeId(),
                            details.getEmail(),
                            true
                    );

                    log.info("Current session retrieved: {}", sessionId);
                    return ResponseEntity.ok(sessionInfo);
                }
            }

            return ResponseEntity.status(401).body(ApiResponseDto.error("No active session found"));
        } catch (Exception e) {
            log.error("Error retrieving current session", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Logout - Terminate current session
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Terminate current session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponseDto> logout() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails details) {

                String sessionId = details.getSessionId();
                if (sessionId != null) {
                    sessionService.terminateSession(sessionId, "User logout");
                    SecurityContextHolder.clearContext();

                    log.info("User logged out successfully: {}", details.getEmployeeId());
                    return ResponseEntity.ok(ApiResponseDto.success("Logout successful"));
                }
            }

            return ResponseEntity.status(401).body(ApiResponseDto.error("No active session"));
        } catch (Exception e) {
            log.error("Error during logout", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Refresh session - Extend session expiry
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh session", description = "Extend current session expiry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session refreshed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Session cannot be refreshed")
    })
    public ResponseEntity<?> refreshSession() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getDetails() instanceof JwtAuthenticationFilter.JwtAuthenticationDetails details) {

                String sessionId = details.getSessionId();
                if (sessionId != null) {
                    sessionService.refreshSession(sessionId);

                    // Generate new JWT token with same session ID
                    UUID userId = UUID.fromString(authentication.getName().substring(5)); // Remove "user-" prefix
                    String newToken;

                    if (authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                        newToken = jwtTokenService.generateAdminToken(
                                userId, details.getEmployeeId(), details.getEmail(), sessionId);
                    } else {
                        newToken = jwtTokenService.generateOfficerToken(
                                userId, details.getEmployeeId(), details.getEmail(), sessionId);
                    }

                    SessionRefreshResponse response = new SessionRefreshResponse(
                            "Session refreshed successfully",
                            newToken,
                            sessionId
                    );

                    log.info("Session refreshed: {} for user: {}", sessionId, details.getEmployeeId());
                    return ResponseEntity.ok(response);
                }
            }

            return ResponseEntity.status(401).body(ApiResponseDto.error("No active session"));
        } catch (Exception e) {
            log.error("Error refreshing session", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Terminate all user sessions (admin only)
     */
    @PostMapping("/terminate-all/{userId}")
    @Operation(summary = "Terminate all user sessions", description = "Admin: Terminate all sessions for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All sessions terminated"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    public ResponseEntity<ApiResponseDto> terminateAllSessions(@PathVariable UUID userId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Check if user is admin
            if (authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {

                sessionService.terminateAllUserSessions(userId, "Admin termination");

                log.info("Admin terminated all sessions for user: {}", userId);
                return ResponseEntity.ok(ApiResponseDto.success("All sessions terminated for user"));
            }

            return ResponseEntity.status(403).body(ApiResponseDto.error("Admin access required"));
        } catch (Exception e) {
            log.error("Error terminating all sessions", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Get active session count for user (admin only)
     */
    @GetMapping("/count/{userId}")
    @Operation(summary = "Get active session count", description = "Admin: Get count of active sessions for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session count retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    public ResponseEntity<?> getSessionCount(@PathVariable UUID userId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Check if user is admin
            if (authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {

                int count = sessionService.getActiveSessionCount(userId);

                SessionCountResponse response = new SessionCountResponse(userId, count);

                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(403).body(ApiResponseDto.error("Admin access required"));
        } catch (Exception e) {
            log.error("Error getting session count", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }
}
