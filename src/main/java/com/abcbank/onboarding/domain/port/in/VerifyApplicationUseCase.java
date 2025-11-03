package com.abcbank.onboarding.domain.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for verifying an onboarding application.
 * This is performed by compliance officers to ensure all documents
 * and information meet regulatory requirements.
 */
public interface VerifyApplicationUseCase {

    /**
     * Verify an application after compliance checks.
     * This marks the application as verified and ready for final approval.
     *
     * @param command the verify application command
     * @return CompletableFuture containing success message
     */
    CompletableFuture<String> verifyApplication(VerifyApplicationCommand command);

    /**
     * Command for verifying an application.
     *
     * @param applicationId the application ID to verify
     * @param verifiedBy the identifier of the compliance officer performing verification
     * @param notes optional notes about the verification
     */
    record VerifyApplicationCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId,

            @NotBlank(message = "Verifier identification is required")
            String verifiedBy,

            String notes
    ) {}
}
