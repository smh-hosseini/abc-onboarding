package com.abcbank.onboarding.adapter.in.web.dto;

import com.abcbank.onboarding.domain.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for document upload.
 * Note: Actual file is passed as MultipartFile in controller.
 * This DTO is used for the document type metadata.
 */
@Schema(description = "Request to upload a document")
public record DocumentUploadRequest(
        @NotNull(message = "Document type is required")
        @Schema(description = "Type of document being uploaded", example = "PASSPORT", allowableValues = {"PASSPORT", "PHOTO"})
        DocumentType documentType
) {
}
