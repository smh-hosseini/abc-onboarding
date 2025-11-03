package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record SuspiciousActivityDetectedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String activityType,
        String details
) implements DomainEvent {
    public SuspiciousActivityDetectedEvent(UUID applicationId, String activityType, String details) {
        this(UUID.randomUUID(), LocalDateTime.now(), "SUSPICIOUS_ACTIVITY_DETECTED", applicationId, activityType, details);
    }
}
