package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationCreatedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String email,
        String phone
) implements DomainEvent {
    public ApplicationCreatedEvent(UUID applicationId, String email, String phone) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_CREATED", applicationId, email, phone);
    }
}
