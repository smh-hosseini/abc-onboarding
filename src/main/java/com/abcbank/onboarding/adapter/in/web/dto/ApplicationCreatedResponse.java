package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO for newly created application.
 */
@Schema(description = "Response after creating a new application")
public record ApplicationCreatedResponse(
        @Schema(description = "Application ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID applicationId,

        @Schema(description = "Success message", example = "Application created successfully. OTP sent to your email and phone.")
        String message
) {
}
