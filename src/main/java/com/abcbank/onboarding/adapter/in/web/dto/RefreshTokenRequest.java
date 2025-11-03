package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for refresh token.
 */
@Schema(description = "Request to refresh access token using refresh token")
public record RefreshTokenRequest(
        @Schema(description = "Refresh token", example = "a1b2c3d4...", required = true)
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
