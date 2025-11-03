package com.abcbank.onboarding.adapter.in.web.auth;

import com.abcbank.onboarding.adapter.in.web.dto.*;
import com.abcbank.onboarding.application.AuthenticationService;
import com.abcbank.onboarding.domain.model.User;
import com.abcbank.onboarding.infrastructure.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for authentication endpoints.
 * Handles both internal user (username/password) and applicant (OTP) authentication.
 *
 * Endpoints:
 * - POST /api/v1/auth/login - Username/password login for internal users
 * - POST /api/v1/auth/refresh - Refresh access token using refresh token
 * - POST /api/v1/auth/logout - Logout and revoke refresh tokens
 * - GET /api/v1/auth/me - Get current user information
 *
 * Note: OTP endpoints for applicants are available in OnboardingController:
 * - POST /api/v1/onboarding/applications/{id}/send-otp
 * - POST /api/v1/onboarding/applications/{id}/verify-otp
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints for internal users")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final JwtTokenService jwtTokenService;

    /**
     * Login with username and password (for internal users: COMPLIANCE_OFFICER, ADMIN).
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login with username and password",
            description = "Authenticates internal users (compliance officers and administrators) using username and password. " +
                    "Returns access token and refresh token for session management."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials or inactive account",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for username: {}", request.username());

        try {
            AuthenticationService.AuthenticationResult result =
                    authenticationService.authenticate(request.username(), request.password());

            // Determine access token expiry based on role
            TokenResponse response = getTokenResponse(result);

            log.info("Login successful for user: {}", request.username());
            return ResponseEntity.ok(response);

        } catch (AuthenticationService.AuthenticationException e) {
            log.warn("Login failed for username: {} - {}", request.username(), e.getMessage());
            throw e;
        }
    }

    private static @NotNull TokenResponse getTokenResponse(AuthenticationService.AuthenticationResult result) {
        long accessTokenExpiry = "ADMIN".equals(result.role()) ? 600000 : 1800000; // 10min for admin, 30min for officer

        return new TokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.userId(),
                result.username(),
                result.email(),
                result.role(),
                accessTokenExpiry / 1000, // Convert to seconds
                result.refreshTokenExpiryMillis() / 1000 // Convert to seconds
        );
    }

    /**
     * Refresh access token using refresh token.
     * Implements token rotation - old refresh token is revoked, new one is issued.
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Uses a refresh token to obtain a new access token and refresh token. " +
                    "Implements refresh token rotation for security - the old refresh token is revoked and a new one is issued. " +
                    "Only works for internal users (not applicants)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = RefreshTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired refresh token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request received");

        try {
            AuthenticationService.RefreshResult result =
                    authenticationService.refreshAccessToken(request.refreshToken());

            RefreshTokenResponse response = new RefreshTokenResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    1800, // Access token expiry in seconds (30 min for now, should be role-based)
                    result.refreshTokenExpiryMillis() / 1000 // Convert to seconds
            );

            log.info("Token refreshed successfully");
            return ResponseEntity.ok(response);

        } catch (AuthenticationService.AuthenticationException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Logout and revoke all refresh tokens for the user.
     * Requires valid access token.
     */
    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Logout user",
            description = "Logs out the current user by revoking all their refresh tokens. " +
                    "The access token will continue to work until it expires naturally (short-lived). " +
                    "Requires a valid access token in the Authorization header."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Logout successful",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing access token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<ApiResponseDto> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        log.info("Logout request received");

        try {
            // Extract token from "Bearer <token>" format
            String token = authHeader.substring(7);

            // Validate and extract user ID from token
            Claims claims = jwtTokenService.validateToken(token);
            if (claims == null) {
                log.warn("Logout failed - invalid token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponseDto.error("Invalid token"));
            }

            // Check if it's an employee token (not applicant)
            String tokenType = claims.get("type", String.class);
            if (!"employee".equals(tokenType)) {
                log.warn("Logout not supported for applicant tokens");
                return ResponseEntity.badRequest()
                        .body(ApiResponseDto.error("Logout is only supported for internal users"));
            }

            UUID userId = jwtTokenService.extractUserId(claims);
            if (userId == null) {
                log.warn("Logout failed - could not extract user ID");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponseDto.error("Invalid token"));
            }

            // Revoke all refresh tokens
            authenticationService.logout(userId);

            log.info("Logout successful for user: {}", userId);
            return ResponseEntity.ok(ApiResponseDto.success("Logged out successfully"));

        } catch (Exception e) {
            log.error("Logout failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Logout failed"));
        }
    }

    /**
     * Get current user information.
     * Requires valid access token.
     */
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get current user information",
            description = "Returns information about the currently authenticated user. " +
                    "Works for both internal users and applicants. " +
                    "Requires a valid access token in the Authorization header."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User information retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserInfoResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing access token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<?> getCurrentUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        log.info("/me request received");

        try {
            // Extract token from "Bearer <token>" format
            String token = authHeader.substring(7);

            // Validate and extract claims from token
            Claims claims = jwtTokenService.validateToken(token);
            if (claims == null) {
                log.warn("/me request failed - invalid token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponseDto.error("Invalid token"));
            }

            // Check token type
            String tokenType = claims.get("type", String.class);

            if ("employee".equals(tokenType)) {
                // Internal user - return full user info
                UUID userId = jwtTokenService.extractUserId(claims);
                if (userId == null) {
                    log.warn("/me request failed - could not extract user ID");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponseDto.error("Invalid token"));
                }

                User user = authenticationService.getUserById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                UserInfoResponse response = new UserInfoResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole().name(),
                        user.isActive(),
                        user.getCreatedAt(),
                        user.getLastLoginAt()
                );

                log.info("/me request successful for user: {}", user.getUsername());
                return ResponseEntity.ok(response);

            } else if ("applicant_session".equals(tokenType)) {
                // Applicant - return minimal info
                UUID applicationId = jwtTokenService.extractApplicationId(claims);
                if (applicationId == null) {
                    log.warn("/me request failed - could not extract application ID");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponseDto.error("Invalid token"));
                }

                // Return basic applicant info
                ApplicantInfoResponse applicantInfo = ApplicantInfoResponse.of(applicationId);

                log.info("/me request successful for applicant: {}", applicationId);
                return ResponseEntity.ok(applicantInfo);

            } else {
                log.warn("/me request failed - unknown token type: {}", tokenType);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponseDto.error("Invalid token type"));
            }

        } catch (Exception e) {
            log.error("/me request failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.error("Failed to retrieve user information"));
        }
    }
}
