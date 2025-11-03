package com.abcbank.onboarding.application;

import com.abcbank.onboarding.adapter.in.web.dto.GdprExportResponse;
import com.abcbank.onboarding.domain.event.DataExportRequestedEvent;
import com.abcbank.onboarding.domain.event.DomainEvent;
import com.abcbank.onboarding.domain.exception.BusinessRuleViolationException;
import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.in.ExportPersonalDataUseCase;
import com.abcbank.onboarding.domain.port.out.AuditRepository;
import com.abcbank.onboarding.domain.port.out.EventPublisher;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Service for GDPR compliance operations.
 *
 * Responsibilities:
 * - Export personal data (GDPR Article 15 - Right to Data Portability)
 * - Request data deletion (GDPR Article 17 - Right to be Forgotten)
 * - Process data deletion requests
 * - Anonymize personal information
 * - Track data processing activities
 *
 * GDPR Compliance:
 * - Data exports in machine-readable format (JSON)
 * - Complete data anonymization for deletion
 * - Audit trail for all GDPR operations
 * - Data retention policies enforcement
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GdprComplianceService implements ExportPersonalDataUseCase {


    private final OnboardingRepository onboardingRepository;
    private final EventPublisher eventPublisher;
    private final AuditRepository auditRepository;

    @Qualifier("asyncExecutor")
    private final Executor asyncExecutor;

    private final ObjectMapper objectMapper = createObjectMapper();

    /**
     * Exports all personal data for an application.
     * Implements GDPR Article 15 - Right to Data Portability.
     *
     * Process:
     * 1. Validate application exists
     * 2. Extract all personal data
     * 3. Convert to JSON format
     * 4. Publish DataExportRequestedEvent
     * 5. Create audit trail
     * 6. Return JSON string
     *
     * @param command ExportDataCommand containing application ID
     * @return CompletableFuture with JSON string of personal data
     * @throws ResourceNotFoundException if application not found
     */
    @Override
    public CompletableFuture<String> exportData(ExportDataCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Exporting personal data for application: {}", command.applicationId());

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Build comprehensive data export
                GdprExportResponse exportData = buildExportData(application);

                // Convert to JSON
                String jsonData = objectMapper.writeValueAsString(exportData);

                // Publish DataExportRequestedEvent
                eventPublisher.publish(new DataExportRequestedEvent(
                    command.applicationId(),
                    application.getEmail()
                ));

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "DATA_EXPORTED",
                    "DATA_SUBJECT",
                    Map.of(
                        "exportedAt", LocalDateTime.now().toString(),
                        "dataSize", jsonData.length(),
                        "format", "JSON"
                    )
                );

                log.info("Successfully exported personal data for application: {} (size: {} bytes)",
                    command.applicationId(), jsonData.length());
                return jsonData;

            } catch (Exception e) {
                log.error("Failed to export personal data for application: {}",
                    command.applicationId(), e);
                throw new BusinessRuleViolationException("Failed to export personal data: " + e.getMessage());
            }
        }, asyncExecutor);
    }

    /**
     * Requests deletion of personal data.
     * Implements GDPR Article 17 - Right to be Forgotten.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application status allows deletion (only REJECTED)
     * 3. Mark application for deletion
     * 4. Save application
     * 5. Publish DataDeletionRequestedEvent
     * 6. Create audit trail
     *
     * @param applicationId UUID of the application
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if deletion not allowed
     */
    public CompletableFuture<String> requestDeletion(UUID applicationId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Requesting data deletion for application: {}", applicationId);

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", applicationId.toString()
                    ));

                // Validate application status - can only delete REJECTED applications
                if (application.getStatus() != ApplicationStatus.REJECTED) {
                    throw new BusinessRuleViolationException(
                        "Can only delete rejected applications. Current status: " +
                        application.getStatus() +
                        ". Approved applications must be retained for legal and regulatory purposes."
                    );
                }

                // Check if already marked for deletion
                if (application.isMarkedForDeletion()) {
                    log.warn("Application {} is already marked for deletion", applicationId);
                    return "Application is already marked for deletion";
                }

                // Mark for deletion (this publishes DataDeletionRequestedEvent)
                application.markForDeletion();

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    applicationId,
                    "DATA_DELETION_REQUESTED",
                    "DATA_SUBJECT",
                    Map.of(
                        "requestedAt", LocalDateTime.now().toString(),
                        "status", application.getStatus().name(),
                        "markedForDeletion", true
                    )
                );

                log.info("Successfully marked application {} for deletion", applicationId);
                return "Data deletion request submitted successfully. " +
                       "Data will be anonymized within the retention period.";

            } catch (Exception e) {
                log.error("Failed to request data deletion for application: {}", applicationId, e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Processes data deletion by anonymizing personal information.
     * This method should be called by a scheduled job after the retention period.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is marked for deletion
     * 3. Validate retention period has passed
     * 4. Anonymize all personal data
     * 5. Save anonymized application
     * 6. Create audit trail
     *
     * Anonymization:
     * - Names replaced with "DELETED"
     * - Email replaced with "deleted@anonymized.local"
     * - Phone replaced with "DELETED"
     * - SSN replaced with "DELETED"
     * - Address replaced with placeholder
     * - Documents remain (metadata only, content should be deleted from storage)
     *
     * @param applicationId UUID of the application
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if deletion not allowed
     */
    public CompletableFuture<String> processDataDeletion(UUID applicationId) {
        return CompletableFuture.supplyAsync(() -> {
            log.warn("Processing data deletion for application: {}", applicationId);

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", applicationId.toString()
                    ));

                // Validate application is marked for deletion
                if (!application.isMarkedForDeletion()) {
                    throw new BusinessRuleViolationException(
                        "Application is not marked for deletion. " +
                        "Please submit a deletion request first."
                    );
                }

                // Validate retention period has passed
                if (application.getDataRetentionUntil() != null &&
                    LocalDateTime.now().isBefore(application.getDataRetentionUntil())) {
                    throw new BusinessRuleViolationException(
                        "Cannot delete data before retention period ends: " +
                        application.getDataRetentionUntil()
                    );
                }

                // Store original email for audit (before anonymization)
                String originalEmail = application.getEmail();

                // Anonymize personal data
                application.anonymize();

                // Save anonymized application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Create audit event (using original email for tracking)
                createAuditEvent(
                    applicationId,
                    "DATA_DELETED_ANONYMIZED",
                    "SYSTEM",
                    Map.of(
                        "deletedAt", LocalDateTime.now().toString(),
                        "originalEmail", originalEmail, // Store in audit for legal compliance
                        "method", "ANONYMIZATION",
                        "gdprCompliant", true
                    )
                );

                log.warn("Successfully anonymized personal data for application: {}", applicationId);
                return "Personal data anonymized successfully in compliance with GDPR";

            } catch (Exception e) {
                log.error("Failed to process data deletion for application: {}", applicationId, e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Builds comprehensive export data structure.
     */
    private GdprExportResponse buildExportData(OnboardingApplication application) {
        // Personal Information
        GdprExportResponse.PersonalInfoExport personalInfo = new GdprExportResponse.PersonalInfoExport(
                application.getFirstName(),
                application.getLastName(),
                application.getGender().name(),
                application.getDateOfBirth(),
                application.getEmail(),
                application.getPhone(),
                application.getNationality(),
                application.getSocialSecurityNumber()
        );

        // Address
        GdprExportResponse.AddressExport address = null;
        if (application.getResidentialAddress() != null) {
            Address addr = application.getResidentialAddress();
            address = new GdprExportResponse.AddressExport(
                    addr.getStreet(),
                    addr.getHouseNumber(),
                    addr.getPostalCode(),
                    addr.getCity(),
                    addr.getCountry()
            );
        }

        // Documents
        var documents = application.getDocuments().stream()
                .map(doc -> new GdprExportResponse.DocumentExport(
                        doc.getId().toString(),
                        doc.getDocumentType().name(),
                        doc.getUploadedAt(),
                        doc.getStatus().name(),
                        doc.getVerifiedAt(),
                        doc.getVerifiedBy()
                ))
                .collect(Collectors.toList());

        // Consents
        var consents = application.getConsents().stream()
                .map(consent -> new GdprExportResponse.ConsentExport(
                        consent.getConsentType().name(),
                        consent.getGrantedAt(),
                        consent.isActive()
                ))
                .collect(Collectors.toList());

        // Account Information (if approved)
        GdprExportResponse.AccountInfoExport accountInfo = null;
        if (application.getCustomerId() != null) {
            accountInfo = new GdprExportResponse.AccountInfoExport(
                    application.getCustomerId().toString(),
                    application.getAccountNumber()
            );
        }

        // GDPR Metadata
        GdprExportResponse.GdprMetadataExport gdprInfo = new GdprExportResponse.GdprMetadataExport(
                application.getDataRetentionUntil(),
                application.isMarkedForDeletion(),
                LocalDateTime.now(),
                "JSON",
                "Article 15 - Right to Data Portability"
        );

        return new GdprExportResponse(
                application.getId().toString(),
                application.getStatus().name(),
                application.getCreatedAt(),
                application.getSubmittedAt(),
                application.getApprovedAt(),
                application.getRejectedAt(),
                personalInfo,
                address,
                documents,
                consents,
                accountInfo,
                gdprInfo
        );
    }

    /**
     * Publishes all domain events from the application aggregate.
     */
    private void publishDomainEvents(OnboardingApplication application) {
        for (DomainEvent event : application.getDomainEvents()) {
            eventPublisher.publish(event);
        }
        application.clearEvents();
    }

    /**
     * Creates an audit event for tracking all operations.
     */
    private void createAuditEvent(UUID applicationId, String eventType, String actor, Map<String, Object> details) {
        try {
            AuditEvent auditEvent = new AuditEvent(
                UUID.randomUUID(),
                applicationId,
                eventType,
                actor,
                null, // IP address - would be set from request context
                null, // User agent - would be set from request context
                new HashMap<>(details)
            );
            auditRepository.save(auditEvent);
        } catch (Exception e) {
            log.error("Failed to create audit event: {}", eventType, e);
            // Don't fail the main operation if audit fails
        }
    }

    /**
     * Creates ObjectMapper configured for JSON serialization.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
