package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationSubmittedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String email
) implements DomainEvent {
    public ApplicationSubmittedEvent(UUID applicationId, String email) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_SUBMITTED", applicationId, email);
    }
}
