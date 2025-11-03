package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for verifying an application (compliance officer action).
 */
@Schema(description = "Request to verify an application after compliance review")
public record VerifyApplicationRequest(
        @Size(max = 500, message = "Notes must not exceed 500 characters")
        @Schema(description = "Optional verification notes", example = "All documents verified and valid")
        String notes
) {
}
