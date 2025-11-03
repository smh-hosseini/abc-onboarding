package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for flagging an application as suspicious.
 */
@Schema(description = "Request to flag an application as suspicious")
public record FlagApplicationRequest(
        @NotBlank(message = "Reason is required")
        @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        @Schema(description = "Reason for flagging as suspicious", example = "Document appears to be forged or manipulated")
        String reason
) {
}
