package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record OtpVerifiedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId
) implements DomainEvent {
    public OtpVerifiedEvent(UUID applicationId) {
        this(UUID.randomUUID(), LocalDateTime.now(), "OTP_VERIFIED", applicationId);
    }
}
