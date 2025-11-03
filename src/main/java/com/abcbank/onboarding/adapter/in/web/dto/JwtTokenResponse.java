package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for JWT token after OTP verification.
 */
@Schema(description = "JWT token response after successful OTP verification")
public record JwtTokenResponse(
        @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Application ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID applicationId,

        @Schema(description = "Token expiration time in seconds", example = "3600")
        long expiresIn
) {
    public JwtTokenResponse(String accessToken, UUID applicationId, long expiresIn) {
        this(accessToken, "Bearer", applicationId, expiresIn);
    }
}
