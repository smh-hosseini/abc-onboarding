package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record DataExportRequestedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String requestedBy
) implements DomainEvent {
    public DataExportRequestedEvent(UUID applicationId, String requestedBy) {
        this(UUID.randomUUID(), LocalDateTime.now(), "DATA_EXPORT_REQUESTED", applicationId, requestedBy);
    }
}
