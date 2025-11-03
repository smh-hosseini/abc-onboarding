package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for applicant providing additional information.
 */
@Schema(description = "Request to provide additional information in response to compliance request")
public record ProvideInfoRequest(
        @NotBlank(message = "Information is required")
        @Size(min = 10, max = 1000, message = "Information must be between 10 and 1000 characters")
        @Schema(description = "Additional information provided by applicant", example = "I have uploaded a clearer passport photo and proof of address document")
        String information
) {
}
