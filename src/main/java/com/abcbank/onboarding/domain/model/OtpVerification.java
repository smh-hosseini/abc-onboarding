package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for OTP verification.
 * Represents a single OTP verification request for a specific channel.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class OtpVerification {

    @EqualsAndHashCode.Include
    private UUID id;
    private UUID applicationId;
    private OtpChannel channel;
    private String otpHash;
    private LocalDateTime expiresAt;
    private int attempts;
    private OtpStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;

    public enum OtpChannel {
        EMAIL,
        SMS
    }

    public enum OtpStatus {
        PENDING,
        VERIFIED,
        EXPIRED,
        MAX_ATTEMPTS_EXCEEDED
    }

    /**
     * Constructor for creating a new OTP verification
     */
    public OtpVerification(
            UUID id,
            UUID applicationId,
            OtpChannel channel,
            String otpHash,
            LocalDateTime expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.applicationId = Objects.requireNonNull(applicationId, "Application ID cannot be null");
        this.channel = Objects.requireNonNull(channel, "Channel cannot be null");
        this.otpHash = Objects.requireNonNull(otpHash, "OTP hash cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiry time cannot be null");
        this.attempts = 0;
        this.status = OtpStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Full constructor for reconstitution from persistence
     */
    public OtpVerification(
            UUID id,
            UUID applicationId,
            OtpChannel channel,
            String otpHash,
            LocalDateTime expiresAt,
            int attempts,
            OtpStatus status,
            LocalDateTime createdAt,
            LocalDateTime verifiedAt
    ) {
        this.id = id;
        this.applicationId = applicationId;
        this.channel = channel;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.attempts = attempts;
        this.status = status;
        this.createdAt = createdAt;
        this.verifiedAt = verifiedAt;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsVerified() {
        this.status = OtpStatus.VERIFIED;
        this.verifiedAt = LocalDateTime.now();
    }

    public void markAsExpired() {
        this.status = OtpStatus.EXPIRED;
    }

    public void markAsMaxAttemptsExceeded() {
        this.status = OtpStatus.MAX_ATTEMPTS_EXCEEDED;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) || status == OtpStatus.EXPIRED;
    }

    public boolean isPending() {
        return status == OtpStatus.PENDING;
    }

    public boolean isVerified() {
        return status == OtpStatus.VERIFIED;
    }
}
