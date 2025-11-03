package com.abcbank.onboarding.domain.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for rejecting an onboarding application.
 * Applications can be rejected due to failed compliance checks,
 * invalid documents, or other policy violations.
 */
public interface RejectApplicationUseCase {

    /**
     * Reject an application with a reason.
     * This sends a rejection notification to the applicant.
     *
     * @param command the reject application command
     * @return CompletableFuture containing success message
     */
    CompletableFuture<String> rejectApplication(RejectApplicationCommand command);

    /**
     * Command for rejecting an application.
     *
     * @param applicationId the application ID to reject
     * @param rejectedBy the identifier of the administrator rejecting the application
     * @param rejectionReason the reason for rejection (will be communicated to the applicant)
     */
    record RejectApplicationCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId,

            @NotBlank(message = "Rejector identification is required")
            String rejectedBy,

            @NotBlank(message = "Rejection reason is required")
            String rejectionReason
    ) {}
}
