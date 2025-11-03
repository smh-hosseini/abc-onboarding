package com.abcbank.onboarding.domain.model;

/**
 * Types of consent required for GDPR compliance
 */
public enum ConsentType {
    /**
     * Consent for processing personal data
     */
    DATA_PROCESSING,

    /**
     * Consent for terms and conditions
     */
    TERMS_AND_CONDITIONS,

    /**
     * Consent for marketing communications
     */
    MARKETING_COMMUNICATIONS
}
