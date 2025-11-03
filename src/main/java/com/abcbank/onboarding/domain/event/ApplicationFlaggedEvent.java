package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationFlaggedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String reason
) implements DomainEvent {
    public ApplicationFlaggedEvent(UUID applicationId, String reason) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_FLAGGED", applicationId, reason);
    }
}
