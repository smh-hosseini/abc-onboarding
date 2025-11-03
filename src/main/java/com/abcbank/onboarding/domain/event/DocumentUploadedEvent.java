package com.abcbank.onboarding.domain.event;

import com.abcbank.onboarding.domain.model.DocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentUploadedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        UUID documentId,
        DocumentType documentType
) implements DomainEvent {
    public DocumentUploadedEvent(UUID applicationId, UUID documentId, DocumentType documentType) {
        this(UUID.randomUUID(), LocalDateTime.now(), "DOCUMENT_UPLOADED", applicationId, documentId, documentType);
    }
}
