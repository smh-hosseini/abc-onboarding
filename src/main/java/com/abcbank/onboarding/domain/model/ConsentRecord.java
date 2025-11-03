package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of user consent for GDPR compliance.
 * Once created, consent records cannot be modified, only revoked.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ConsentRecord {
    @EqualsAndHashCode.Include
    private final UUID id;
    private final ConsentType consentType;
    private final boolean granted;
    private final LocalDateTime grantedAt;
    private final String ipAddress;
    private final String consentText;
    private final String version;
    private LocalDateTime revokedAt;

    public ConsentRecord(
            UUID id,
            ConsentType consentType,
            boolean granted,
            String ipAddress,
            String consentText,
            String version
    ) {
        this.id = Objects.requireNonNull(id, "Consent ID cannot be null");
        this.consentType = Objects.requireNonNull(consentType, "Consent type cannot be null");
        this.granted = granted;
        this.grantedAt = LocalDateTime.now();
        this.ipAddress = ipAddress;
        this.consentText = Objects.requireNonNull(consentText, "Consent text cannot be null");
        this.version = Objects.requireNonNull(version, "Version cannot be null");
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return granted && revokedAt == null;
    }
}
