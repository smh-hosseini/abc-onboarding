package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerCreatedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID customerId,
        UUID applicationId,
        String accountNumber
) implements DomainEvent {
    public CustomerCreatedEvent(UUID customerId, UUID applicationId, String accountNumber) {
        this(UUID.randomUUID(), LocalDateTime.now(), "CUSTOMER_CREATED", customerId, applicationId, accountNumber);
    }
}
