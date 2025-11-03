package com.abcbank.onboarding.adapter.in.web.dto;

import com.abcbank.onboarding.domain.model.DocumentStatus;
import com.abcbank.onboarding.domain.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for document information.
 */
@Schema(description = "Document information")
public record DocumentResponse(
        @Schema(description = "Document ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Document type", example = "PASSPORT")
        DocumentType documentType,

        @Schema(description = "Document status", example = "VERIFIED")
        DocumentStatus status,

        @Schema(description = "MIME type", example = "image/jpeg")
        String mimeType,

        @Schema(description = "File size in bytes", example = "1048576")
        Long fileSize,

        @Schema(description = "Upload timestamp", example = "2025-01-15T10:45:00")
        LocalDateTime uploadedAt,

        @Schema(description = "Verification timestamp", example = "2025-01-16T09:30:00")
        LocalDateTime verifiedAt,

        @Schema(description = "Verified by (compliance officer)", example = "officer@abcbank.com")
        String verifiedBy,

        @Schema(description = "Rejection reason (if rejected)", example = "Image not clear enough")
        String rejectionReason
) {
}
