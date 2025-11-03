package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionTerminatedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID sessionId,
        UUID userId,
        String reason
) implements DomainEvent {
    public SessionTerminatedEvent(UUID sessionId, UUID userId, String reason) {
        this(UUID.randomUUID(), LocalDateTime.now(), "SESSION_TERMINATED", sessionId, userId, reason);
    }
}
