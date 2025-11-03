package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user information (/me endpoint).
 */
@Schema(description = "Current user information")
public record UserInfoResponse(
        @Schema(description = "User ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,

        @Schema(description = "Username", example = "officer@abc.nl")
        String username,

        @Schema(description = "Email", example = "officer@abc.nl")
        String email,

        @Schema(description = "Full name", example = "Test Compliance Officer")
        String fullName,

        @Schema(description = "User role", example = "COMPLIANCE_OFFICER")
        String role,

        @Schema(description = "Account active status", example = "true")
        boolean active,

        @Schema(description = "Account creation timestamp")
        LocalDateTime createdAt,

        @Schema(description = "Last login timestamp")
        LocalDateTime lastLoginAt
) {}
