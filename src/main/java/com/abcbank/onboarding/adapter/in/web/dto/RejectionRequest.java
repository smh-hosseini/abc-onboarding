package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for rejecting an application (admin action).
 */
@Schema(description = "Request to reject an application")
public record RejectionRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(min = 10, max = 500, message = "Rejection reason must be between 10 and 500 characters")
        @Schema(description = "Reason for rejection", example = "Incomplete documentation or failed verification checks")
        String rejectionReason
) {
}
