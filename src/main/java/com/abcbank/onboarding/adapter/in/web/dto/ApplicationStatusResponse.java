package com.abcbank.onboarding.adapter.in.web.dto;

import com.abcbank.onboarding.domain.model.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for application status (lightweight).
 */
@Schema(description = "Application status information")
public record ApplicationStatusResponse(
        @Schema(description = "Application ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Current status", example = "SUBMITTED")
        ApplicationStatus status,

        @Schema(description = "Application creation timestamp", example = "2025-01-15T10:30:00")
        LocalDateTime createdAt,

        @Schema(description = "Last update timestamp", example = "2025-01-15T11:00:00")
        LocalDateTime lastUpdated
) {
}
