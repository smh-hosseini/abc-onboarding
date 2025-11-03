package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdditionalInfoRequestedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String reason
) implements DomainEvent {
    public AdditionalInfoRequestedEvent(UUID applicationId, String reason) {
        this(UUID.randomUUID(), LocalDateTime.now(), "ADDITIONAL_INFO_REQUESTED", applicationId, reason);
    }
}
