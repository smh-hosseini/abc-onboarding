package com.abcbank.onboarding.domain.model;

/**
 * User roles for internal authentication.
 * APPLICANT role is handled separately via OTP-based authentication.
 */
public enum UserRole {
    /**
     * Compliance officer who reviews and verifies applications
     */
    COMPLIANCE_OFFICER,

    /**
     * Administrator who approves/rejects applications and manages system
     */
    ADMIN
}
