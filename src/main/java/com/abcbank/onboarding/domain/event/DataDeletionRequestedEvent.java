package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record DataDeletionRequestedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId
) implements DomainEvent {
    public DataDeletionRequestedEvent(UUID applicationId) {
        this(UUID.randomUUID(), LocalDateTime.now(), "DATA_DELETION_REQUESTED", applicationId);
    }
}
