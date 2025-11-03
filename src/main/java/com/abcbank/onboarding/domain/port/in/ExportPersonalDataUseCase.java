package com.abcbank.onboarding.domain.port.in;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Use case for exporting personal data in compliance with GDPR Article 15.
 * Allows individuals to obtain a copy of their personal data in a structured,
 * commonly used, and machine-readable format (JSON).
 */
public interface ExportPersonalDataUseCase {

    /**
     * Export all personal data associated with an application.
     * Returns data in JSON format for portability.
     *
     * @param command the export data command
     * @return CompletableFuture containing the exported data as JSON string
     */
    CompletableFuture<String> exportData(ExportDataCommand command);

    /**
     * Command for exporting personal data.
     *
     * @param applicationId the application ID whose data should be exported
     */
    record ExportDataCommand(
            @NotNull(message = "Application ID is required")
            UUID applicationId
    ) {}
}
