package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationAssignedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String assignedTo
) implements DomainEvent {
    public ApplicationAssignedEvent(UUID applicationId, String assignedTo) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_ASSIGNED", applicationId, assignedTo);
    }
}
