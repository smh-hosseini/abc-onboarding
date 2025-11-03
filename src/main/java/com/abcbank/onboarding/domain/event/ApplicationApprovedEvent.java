package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplicationApprovedEvent(
        UUID eventId,
        LocalDateTime occurredAt,
        String eventType,
        UUID applicationId,
        UUID customerId,
        String accountNumber,
        String approvedBy,
        String email
) implements DomainEvent {
    public ApplicationApprovedEvent(UUID applicationId, UUID customerId, String accountNumber,
                                   String approvedBy, String email) {
        this(UUID.randomUUID(), LocalDateTime.now(), "APPLICATION_APPROVED",
             applicationId, customerId, accountNumber, approvedBy, email);
    }
}
