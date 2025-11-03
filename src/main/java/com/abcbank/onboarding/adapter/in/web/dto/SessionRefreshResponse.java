package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for session refresh operation.
 */
@Schema(description = "Session refresh response")
public record SessionRefreshResponse(
        @Schema(description = "Response message", example = "Session refreshed successfully")
        String message,

        @Schema(description = "New JWT token")
        String token,

        @Schema(description = "Session ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId
) {}
