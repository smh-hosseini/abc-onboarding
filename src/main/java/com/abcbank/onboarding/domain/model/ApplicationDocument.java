package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing an uploaded document.
 * Part of the OnboardingApplication aggregate.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ApplicationDocument {
    @EqualsAndHashCode.Include
    private UUID id;
    private DocumentType documentType;
    private String storagePath;
    private String mimeType;
    private Long fileSize;
    private DocumentStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
    private String rejectionReason;

    public ApplicationDocument(
            UUID id,
            DocumentType documentType,
            String storagePath,
            String mimeType,
            Long fileSize
    ) {
        this.id = Objects.requireNonNull(id, "Document ID cannot be null");
        this.documentType = Objects.requireNonNull(documentType, "Document type cannot be null");
        this.storagePath = Objects.requireNonNull(storagePath, "Storage path cannot be null");
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.status = DocumentStatus.UPLOADED;
        this.uploadedAt = LocalDateTime.now();
    }

    public void verify(String verifiedBy) {
        this.status = DocumentStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
        this.verifiedBy = verifiedBy;
    }

    public void reject(String reason) {
        this.status = DocumentStatus.REJECTED;
        this.rejectionReason = Objects.requireNonNull(reason, "Rejection reason cannot be null");
    }

    public boolean isVerified() {
        return status == DocumentStatus.VERIFIED;
    }
}
