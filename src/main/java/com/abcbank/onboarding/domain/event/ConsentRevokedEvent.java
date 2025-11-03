package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConsentRevokedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String consentType
) implements DomainEvent {
    public ConsentRevokedEvent(UUID applicationId, String consentType) {
        this(UUID.randomUUID(), LocalDateTime.now(), "CONSENT_REVOKED", applicationId, consentType);
    }
}
