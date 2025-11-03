package com.abcbank.onboarding.adapter.in.web.dto;

import com.abcbank.onboarding.domain.model.ApplicationStatus;
import com.abcbank.onboarding.domain.model.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for complete application details.
 * Excludes sensitive data like OTP hash and shows masked SSN.
 */
@Schema(description = "Complete application details")
public record ApplicationResponse(
        @Schema(description = "Application ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Application status", example = "SUBMITTED")
        ApplicationStatus status,

        @Schema(description = "First name", example = "John")
        String firstName,

        @Schema(description = "Last name", example = "Doe")
        String lastName,

        @Schema(description = "Gender", example = "MALE")
        Gender gender,

        @Schema(description = "Date of birth", example = "1990-01-15")
        LocalDate dateOfBirth,

        @Schema(description = "Phone number", example = "+31612345678")
        String phone,

        @Schema(description = "Email address", example = "john.doe@example.com")
        String email,

        @Schema(description = "Nationality code", example = "NL")
        String nationality,

        @Schema(description = "Residential address")
        AddressResponse residentialAddress,

        @Schema(description = "Masked Social Security Number (shows only last 4 digits)", example = "XXX-XX-6789")
        String socialSecurityNumber,

        @Schema(description = "List of uploaded documents")
        List<DocumentResponse> documents,

        @Schema(description = "Account number (only present if approved)", example = "NL12ABCB0123456789")
        String accountNumber,

        @Schema(description = "Assigned compliance officer", example = "officer@abcbank.com")
        String assignedTo,

        @Schema(description = "Requires manual review flag")
        boolean requiresManualReview,

        @Schema(description = "Review reason", example = "Additional verification needed")
        String reviewReason,

        @Schema(description = "Rejection reason", example = "Incomplete documentation")
        String rejectionReason,

        @Schema(description = "Application creation timestamp", example = "2025-01-15T10:30:00")
        LocalDateTime createdAt,

        @Schema(description = "Application submission timestamp", example = "2025-01-15T11:00:00")
        LocalDateTime submittedAt,

        @Schema(description = "Application approval timestamp", example = "2025-01-16T14:00:00")
        LocalDateTime approvedAt,

        @Schema(description = "Application rejection timestamp", example = "2025-01-16T14:00:00")
        LocalDateTime rejectedAt
) {
}
