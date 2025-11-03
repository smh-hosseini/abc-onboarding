package com.abcbank.onboarding.domain.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Marker interface for all domain events.
 * Domain events represent significant business occurrences.
 * All event records implement this interface.
 */
public interface DomainEvent {
    UUID eventId();
    LocalDateTime occurredAt();
    String eventType();
}
