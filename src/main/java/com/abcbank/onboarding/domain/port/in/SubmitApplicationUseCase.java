package com.abcbank.onboarding.domain.port.in;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for submitting an onboarding application for review.
 * Once submitted, the application moves into the review pipeline.
 */
public interface SubmitApplicationUseCase {

    /**
     * Submit an application for review.
     * The application must have all required documents uploaded and OTP verified.
     *
     * @param command the submit application command
     * @return CompletableFuture containing success message
     */
    CompletableFuture<String> submitForReview(SubmitApplicationCommand command);

    /**
     * Command for submitting an application for review.
     *
     * @param applicationId the application ID to submit
     */
    record SubmitApplicationCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId
    ) {}
}
