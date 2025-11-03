package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for refresh token operation.
 */
@Schema(description = "Response with new access and refresh tokens")
public record RefreshTokenResponse(
        @Schema(description = "New JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "New refresh token (rotated)", example = "a1b2c3d4...")
        String refreshToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token expiration time in seconds", example = "1800")
        long accessTokenExpiresIn,

        @Schema(description = "Refresh token expiration time in seconds", example = "2592000")
        long refreshTokenExpiresIn
) {
    public RefreshTokenResponse(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            long refreshTokenExpiresIn
    ) {
        this(accessToken, refreshToken, "Bearer", accessTokenExpiresIn, refreshTokenExpiresIn);
    }
}
