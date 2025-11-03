package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdditionalInfoProvidedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String information
) implements DomainEvent {
    public AdditionalInfoProvidedEvent(UUID applicationId, String information) {
        this(UUID.randomUUID(), LocalDateTime.now(), "ADDITIONAL_INFO_PROVIDED", applicationId, information);
    }
}
