package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for current session information.
 */
@Schema(description = "Current session information")
public record CurrentSessionResponse(
        @Schema(description = "Session ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String sessionId,

        @Schema(description = "Employee ID", example = "EMP001")
        String employeeId,

        @Schema(description = "Email address", example = "john.doe@abcbank.com")
        String email,

        @Schema(description = "Session active status", example = "true")
        boolean active
) {}
