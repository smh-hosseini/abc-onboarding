package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record OtpSentEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        String destination,
        String channel
) implements DomainEvent {
    public OtpSentEvent(UUID applicationId, String destination, String channel) {
        this(UUID.randomUUID(), LocalDateTime.now(), "OTP_SENT", applicationId, destination, channel);
    }
}
