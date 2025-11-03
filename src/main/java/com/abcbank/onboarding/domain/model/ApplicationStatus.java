package com.abcbank.onboarding.domain.model;

/**
 * Enum representing the various states an onboarding application can be in.
 * State transitions are enforced by the domain model.
 */
public enum ApplicationStatus {
    /**
     * Application has been created but OTP not yet verified
     */
    INITIATED,

    /**
     * OTP has been successfully verified
     */
    OTP_VERIFIED,

    /**
     * Required documents have been uploaded
     */
    DOCUMENTS_UPLOADED,

    /**
     * Application has been submitted for review
     */
    SUBMITTED,

    /**
     * Compliance officer is reviewing the application
     */
    UNDER_REVIEW,

    /**
     * Compliance officer has verified all documents
     */
    VERIFIED,

    /**
     * Additional information is required from applicant
     */
    REQUIRES_MORE_INFO,

    /**
     * Application has been flagged as suspicious
     */
    FLAGGED_SUSPICIOUS,

    /**
     * Application has been approved and customer account created
     */
    APPROVED,

    /**
     * Application has been rejected
     */
    REJECTED
}
