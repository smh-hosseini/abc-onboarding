package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record RateLimitExceededEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        String resource,
        String identifier,
        int limit
) implements DomainEvent {
    public RateLimitExceededEvent(String resource, String identifier, int limit) {
        this(UUID.randomUUID(), LocalDateTime.now(), "RATE_LIMIT_EXCEEDED", resource, identifier, limit);
    }
}
