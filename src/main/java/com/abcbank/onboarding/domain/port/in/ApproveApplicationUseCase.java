package com.abcbank.onboarding.domain.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for approving an onboarding application.
 * This is the final step performed by administrators to create the customer account.
 */
public interface ApproveApplicationUseCase {

    /**
     * Approve an application and create the customer account.
     * This triggers account creation and sends approval notification to the customer.
     *
     * @param command the approve application command
     * @return CompletableFuture containing the account number
     */
    CompletableFuture<String> approveApplication(ApproveApplicationCommand command);

    /**
     * Command for approving an application.
     *
     * @param applicationId the application ID to approve
     * @param approvedBy the identifier of the administrator approving the application
     * @param approvalNotes optional notes about the approval decision
     */
    record ApproveApplicationCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId,

            @NotBlank(message = "Approver identification is required")
            String approvedBy,

            String approvalNotes
    ) {}
}
