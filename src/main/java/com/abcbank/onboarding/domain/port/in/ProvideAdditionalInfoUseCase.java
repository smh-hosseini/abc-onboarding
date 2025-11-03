package com.abcbank.onboarding.domain.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for applicants to provide additional information when requested.
 * This allows applicants to respond to compliance officer requests for more info.
 */
public interface ProvideAdditionalInfoUseCase {

    /**
     * Provide additional information for an application.
     * The application must be in REQUIRES_MORE_INFO status.
     *
     * @param command the provide additional info command
     * @return CompletableFuture containing success message
     */
    CompletableFuture<String> provideAdditionalInfo(ProvideInfoCommand command);

    /**
     * Command for providing additional information.
     *
     * @param applicationId the application ID
     * @param information the additional information provided by applicant
     */
    record ProvideInfoCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId,

            @NotBlank(message = "Information is required")
            String information
    ) {}
}
