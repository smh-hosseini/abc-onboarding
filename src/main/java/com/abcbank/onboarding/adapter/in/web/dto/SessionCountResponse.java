package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for active session count.
 */
@Schema(description = "Active session count information")
public record SessionCountResponse(
        @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "Number of active sessions", example = "2")
        int activeSessionCount
) {}
