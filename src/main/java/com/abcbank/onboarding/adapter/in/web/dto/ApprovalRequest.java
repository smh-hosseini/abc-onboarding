package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for approving an application (admin action).
 */
@Schema(description = "Request to approve an application")
public record ApprovalRequest(
        @Size(max = 500, message = "Approval notes must not exceed 500 characters")
        @Schema(description = "Optional approval notes", example = "Application approved - all checks passed")
        String approvalNotes
) {
}
