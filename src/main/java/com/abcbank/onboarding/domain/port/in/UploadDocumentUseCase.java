package com.abcbank.onboarding.domain.port.in;

import com.abcbank.onboarding.domain.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for uploading documents for an onboarding application.
 * Documents are required for identity verification and compliance.
 */
public interface UploadDocumentUseCase {

    /**
     * Upload a document for the application.
     *
     * @param command the upload document command
     * @return CompletableFuture containing the document ID
     */
    CompletableFuture<UUID> uploadDocument(UploadDocumentCommand command);

    /**
     * Command for uploading a document.
     *
     * @param applicationId the application ID
     * @param documentType the type of document being uploaded
     * @param content the document content as byte array
     * @param filename the original filename
     * @param contentType the MIME content type (e.g., "image/jpeg", "application/pdf")
     */
    record UploadDocumentCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId,

            @NotNull(message = "Document type is required")
            DocumentType documentType,

            @NotNull(message = "Document content is required")
            byte[] content,

            @NotBlank(message = "Filename is required")
            String filename,

            @NotBlank(message = "Content type is required")
            String contentType
    ) {}
}
