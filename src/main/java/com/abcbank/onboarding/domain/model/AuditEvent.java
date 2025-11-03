package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit event for tracking all actions in the system.
 * Provides complete audit trail for compliance.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditEvent {
    @EqualsAndHashCode.Include
    private final UUID id;
    private final UUID applicationId;
    private final String eventType;
    private final LocalDateTime timestamp;
    private final String actor; // Who performed the action
    private final String ipAddress;
    private final String userAgent;
    private final Map<String, Object> eventDetails;

    public AuditEvent(
            UUID id,
            UUID applicationId,
            String eventType,
            String actor,
            String ipAddress,
            String userAgent,
            Map<String, Object> eventDetails
    ) {
        this.id = Objects.requireNonNull(id, "Audit event ID cannot be null");
        this.applicationId = applicationId; // Can be null for system-level events
        this.eventType = Objects.requireNonNull(eventType, "Event type cannot be null");
        this.timestamp = LocalDateTime.now();
        this.actor = Objects.requireNonNull(actor, "Actor cannot be null");
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.eventDetails = eventDetails;
    }
}
