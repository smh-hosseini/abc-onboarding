package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.event.DomainEvent;
import com.abcbank.onboarding.domain.exception.BusinessRuleViolationException;
import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.in.CreateApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.ProvideAdditionalInfoUseCase;
import com.abcbank.onboarding.domain.port.in.SubmitApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.UploadDocumentUseCase;
import com.abcbank.onboarding.domain.port.out.AuditRepository;
import com.abcbank.onboarding.domain.port.out.EventPublisher;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import com.abcbank.onboarding.domain.port.out.StorageService;
import com.abcbank.onboarding.domain.service.DuplicateDetectionService;
import com.abcbank.onboarding.domain.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Main orchestration service for onboarding application operations.
 * Implements all application use cases for customer onboarding workflow.
 *
 * Responsibilities:
 * - Create new onboarding applications
 * - Upload and manage documents
 * - Submit applications for review
 * - Coordinate between domain services
 * - Publish domain events
 * - Create audit trail
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OnboardingApplicationService implements
        CreateApplicationUseCase,
        UploadDocumentUseCase,
        SubmitApplicationUseCase,
        ProvideAdditionalInfoUseCase {


    private final OnboardingRepository onboardingRepository;
    private final OtpService otpService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final EventPublisher eventPublisher;
    private final StorageService storageService;
    private final AuditRepository auditRepository;

    @Qualifier("asyncExecutor")
    private final Executor asyncExecutor;

    /**
     * Creates a new onboarding application.
     *
     * Process:
     * 1. Validate input data
     * 2. Check for duplicate customers (SSN, email, phone)
     * 3. Create application aggregate
     * 4. Generate OTP
     * 5. Hash and save OTP
     * 6. Save application
     * 7. Publish ApplicationCreatedEvent
     * 8. Create audit trail
     *
     * @param command CreateApplicationCommand containing applicant details
     * @return UUID of the created application
     * @throws BusinessRuleViolationException if duplicate customer detected or validation fails
     */
    @Override
    public UUID execute(CreateApplicationCommand command) {
        log.info("Creating new onboarding application for email: {}", command.email());

        try {
            // Validate command
            validateCreateCommand(command);

            // Check for duplicate customers
            duplicateDetectionService.checkDuplicates(
                command.socialSecurityNumber(),
                command.email(),
                command.phone(),
                onboardingRepository
            );

            // Create application aggregate
            UUID applicationId = UUID.randomUUID();
            OnboardingApplication application = new OnboardingApplication(
                applicationId,
                command.firstName(),
                command.lastName(),
                command.gender(),
                command.dateOfBirth(),
                command.phone(),
                command.email(),
                command.nationality(),
                command.residentialAddress(),
                command.socialSecurityNumber()
            );

            // Generate and set OTP
            String otp = otpService.generateOtp();
            String otpHash = otpService.hashOtp(otp);
            LocalDateTime otpExpiry = otpService.generateOtpExpiryTime();

            // Save application
            OnboardingApplication savedApplication = onboardingRepository.save(application);

            // Publish domain events
            publishDomainEvents(savedApplication);

            // Create audit event
            createAuditEvent(
                applicationId,
                "APPLICATION_CREATED",
                "SYSTEM",
                Map.of(
                    "email", command.email(),
                    "firstName", command.firstName(),
                    "lastName", command.lastName()
                )
            );

            log.info("Successfully created application with ID: {}", applicationId);
            return applicationId;

        } catch (Exception e) {
            log.error("Failed to create application for email: {}", command.email(), e);
            throw e;
        }
    }

    /**
     * Uploads a document for an onboarding application.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application status allows document upload
     * 3. Store document in storage service
     * 4. Create ApplicationDocument entity
     * 5. Add document to application
     * 6. Save application
     * 7. Publish DocumentUploadedEvent
     * 8. Create audit trail
     *
     * @param command UploadDocumentCommand containing document details
     * @return CompletableFuture with document UUID
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if document upload not allowed
     */
    @Override
    public CompletableFuture<UUID> uploadDocument(UploadDocumentCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Uploading document type {} for application {}",
                command.documentType(), command.applicationId());

            try {
                // Validate command
                validateUploadCommand(command);

                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.OTP_VERIFIED &&
                    application.getStatus() != ApplicationStatus.DOCUMENTS_UPLOADED) {
                    throw new BusinessRuleViolationException(
                        "Cannot upload documents for application in status: " + application.getStatus()
                    );
                }

                // Store document in storage service
                String storagePath = storageService.store(
                    command.content(),
                    command.filename(),
                    command.contentType()
                );

                // Create ApplicationDocument entity
                UUID documentId = UUID.randomUUID();
                ApplicationDocument document = new ApplicationDocument(
                    documentId,
                    command.documentType(),
                    storagePath,
                    command.contentType(),
                    (long) command.content().length
                );

                // Add document to application (this publishes DocumentUploadedEvent)
                application.addDocument(document);

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "DOCUMENT_UPLOADED",
                    "APPLICANT",
                    Map.of(
                        "documentId", documentId.toString(),
                        "documentType", command.documentType().name(),
                        "filename", command.filename(),
                        "fileSize", command.content().length
                    )
                );

                log.info("Successfully uploaded document {} for application {}",
                    documentId, command.applicationId());
                return documentId;

            } catch (Exception e) {
                log.error("Failed to upload document for application {}",
                    command.applicationId(), e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Submits an application for review.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate all required documents uploaded
     * 3. Validate all required consents granted
     * 4. Submit application (transitions to SUBMITTED status)
     * 5. Save application
     * 6. Publish ApplicationSubmittedEvent
     * 7. Create audit trail
     *
     * @param command SubmitApplicationCommand containing application ID
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if submission requirements not met
     */
    @Override
    public CompletableFuture<String> submitForReview(SubmitApplicationCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Submitting application {} for review", command.applicationId());

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Validate application has required documents
                if (!application.hasAllRequiredDocuments()) {
                    throw new BusinessRuleViolationException(
                        "Application must have all required documents before submission. " +
                        "Required: PASSPORT, PHOTO"
                    );
                }

                // Validate application has required consents
                if (!application.hasRequiredConsents()) {
                    throw new BusinessRuleViolationException(
                        "Application must have all required consents before submission. " +
                        "Required: DATA_PROCESSING, TERMS_AND_CONDITIONS"
                    );
                }

                // Submit application (this publishes ApplicationSubmittedEvent)
                application.submit();

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "APPLICATION_SUBMITTED",
                    "APPLICANT",
                    Map.of(
                        "status", ApplicationStatus.SUBMITTED.name(),
                        "submittedAt", LocalDateTime.now().toString()
                    )
                );

                log.info("Successfully submitted application {} for review", command.applicationId());
                return "Application submitted successfully and is now under review";

            } catch (Exception e) {
                log.error("Failed to submit application {}", command.applicationId(), e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Provides additional information for an application that requires more info.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is in REQUIRES_MORE_INFO status
     * 3. Provide additional information (transitions to UNDER_REVIEW status)
     * 4. Save application
     * 5. Publish AdditionalInfoProvidedEvent
     * 6. Create audit trail
     *
     * @param command ProvideInfoCommand containing application ID and information
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if application not in correct status
     */
    @Override
    public CompletableFuture<String> provideAdditionalInfo(ProvideInfoCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Providing additional info for application {}", command.applicationId());

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Provide additional information (this publishes AdditionalInfoProvidedEvent)
                application.provideAdditionalInformation(command.information());

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "ADDITIONAL_INFO_PROVIDED",
                    "APPLICANT",
                    Map.of(
                        "status", ApplicationStatus.UNDER_REVIEW.name(),
                        "providedAt", LocalDateTime.now().toString()
                    )
                );

                log.info("Successfully provided additional info for application {}", command.applicationId());
                return "Additional information provided successfully. Application returned to review.";

            } catch (Exception e) {
                log.error("Failed to provide additional info for application {}", command.applicationId(), e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Validates create application command.
     */
    private void validateCreateCommand(CreateApplicationCommand command) {
        if (command.firstName() == null || command.firstName().trim().isEmpty()) {
            throw new BusinessRuleViolationException("First name is required");
        }
        if (command.lastName() == null || command.lastName().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Last name is required");
        }
        if (command.email() == null || command.email().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Email is required");
        }
        if (command.phone() == null || command.phone().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Phone is required");
        }
        if (command.socialSecurityNumber() == null || command.socialSecurityNumber().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Social Security Number is required");
        }
        if (command.dateOfBirth() == null) {
            throw new BusinessRuleViolationException("Date of birth is required");
        }
        if (command.dateOfBirth().isAfter(LocalDateTime.now().toLocalDate().minusYears(18))) {
            throw new BusinessRuleViolationException("Applicant must be at least 18 years old");
        }
    }

    /**
     * Validates upload document command.
     */
    private void validateUploadCommand(UploadDocumentCommand command) {
        if (command.content() == null || command.content().length == 0) {
            throw new BusinessRuleViolationException("Document content is required");
        }
        if (command.content().length > 10 * 1024 * 1024) { // 10MB limit
            throw new BusinessRuleViolationException("Document size exceeds maximum limit of 10MB");
        }
        if (command.filename() == null || command.filename().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Filename is required");
        }
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
                null, // IP address - would be set from request context in real implementation
                null, // User agent - would be set from request context in real implementation
                new HashMap<>(details)
            );
            auditRepository.save(auditEvent);
        } catch (Exception e) {
            log.error("Failed to create audit event: {}", eventType, e);
            // Don't fail the main operation if audit fails
        }
    }
}
