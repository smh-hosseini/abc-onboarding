package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for successful authentication with access and refresh tokens.
 */
@Schema(description = "Authentication response with access and refresh tokens")
public record TokenResponse(
        @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Refresh token for obtaining new access tokens", example = "a1b2c3d4...")
        String refreshToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "User ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,

        @Schema(description = "Username", example = "officer@abc.nl")
        String username,

        @Schema(description = "Email", example = "officer@abc.nl")
        String email,

        @Schema(description = "User role", example = "COMPLIANCE_OFFICER")
        String role,

        @Schema(description = "Access token expiration time in seconds", example = "1800")
        long accessTokenExpiresIn,

        @Schema(description = "Refresh token expiration time in seconds", example = "2592000")
        long refreshTokenExpiresIn
) {
    public TokenResponse(
            String accessToken,
            String refreshToken,
            UUID userId,
            String username,
            String email,
            String role,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn
    ) {
        this(accessToken, refreshToken, "Bearer", userId, username, email, role, accessTokenExpiresIn, refreshTokenExpiresIn);
    }
}
