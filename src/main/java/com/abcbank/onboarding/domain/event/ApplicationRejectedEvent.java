package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationRejectedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String reason,
        String email
) implements DomainEvent {
    public ApplicationRejectedEvent(UUID applicationId, String reason, String email) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_REJECTED", applicationId, reason, email);
    }
}
