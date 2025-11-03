package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentRejectedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        UUID documentId,
        String reason
) implements DomainEvent {
    public DocumentRejectedEvent(UUID applicationId, UUID documentId, String reason) {
        this(UUID.randomUUID(), LocalDateTime.now(), "DOCUMENT_REJECTED", applicationId, documentId, reason);
    }
}
