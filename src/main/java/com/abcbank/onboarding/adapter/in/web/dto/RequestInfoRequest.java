package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for requesting additional information from applicant.
 */
@Schema(description = "Request to ask for additional information from applicant")
public record RequestInfoRequest(
        @NotBlank(message = "Reason is required")
        @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        @Schema(description = "Reason for requesting additional information", example = "Please provide clearer photo of passport")
        String reason
) {
}
