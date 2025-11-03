package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentVerifiedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        UUID documentId,
        String verifiedBy
) implements DomainEvent {
    public DocumentVerifiedEvent(UUID applicationId, UUID documentId, String verifiedBy) {
        this(UUID.randomUUID(), LocalDateTime.now(), "DOCUMENT_VERIFIED", applicationId, documentId, verifiedBy);
    }
}
