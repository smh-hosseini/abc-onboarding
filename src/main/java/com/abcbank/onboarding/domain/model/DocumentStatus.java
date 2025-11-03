package com.abcbank.onboarding.domain.model;

/**
 * Status of an uploaded document
 */
public enum DocumentStatus {
    /**
     * Document has been uploaded
     */
    UPLOADED,

    /**
     * Document has been verified by compliance officer
     */
    VERIFIED,

    /**
     * Document has been rejected
     */
    REJECTED
}
