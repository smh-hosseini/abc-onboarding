package com.abcbank.onboarding.domain.event;

import com.abcbank.onboarding.domain.model.ConsentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConsentGrantedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        ConsentType consentType
) implements DomainEvent {
    public ConsentGrantedEvent(UUID applicationId, ConsentType consentType) {
        this(UUID.randomUUID(), LocalDateTime.now(), "CONSENT_GRANTED", applicationId, consentType);
    }
}
