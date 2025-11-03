package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationVerifiedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String verifiedBy
) implements DomainEvent {
    public ApplicationVerifiedEvent(UUID applicationId, String verifiedBy) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_VERIFIED", applicationId, verifiedBy);
    }
}
