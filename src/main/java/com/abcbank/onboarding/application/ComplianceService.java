package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.event.DomainEvent;
import com.abcbank.onboarding.domain.exception.BusinessRuleViolationException;
import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.ApplicationStatus;
import com.abcbank.onboarding.domain.model.AuditEvent;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.port.in.VerifyApplicationUseCase;
import com.abcbank.onboarding.domain.port.out.AuditRepository;
import com.abcbank.onboarding.domain.port.out.EventPublisher;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Service for compliance officer operations.
 *
 * Responsibilities:
 * - Verify applications and documents
 * - Request additional information from applicants
 * - Flag suspicious applications
 * - Assign applications to compliance officers
 * - List applications by status for review
 *
 * This service implements the compliance review workflow and ensures
 * regulatory requirements are met before applications are approved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ComplianceService implements VerifyApplicationUseCase {

    private final OnboardingRepository onboardingRepository;
    private final EventPublisher eventPublisher;
    private final AuditRepository auditRepository;

    @Qualifier("asyncExecutor")
    private final Executor asyncExecutor;

    /**
     * Verifies an application after compliance review.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is under review
     * 3. Verify all documents
     * 4. Transition application to VERIFIED status
     * 5. Save application
     * 6. Publish ApplicationVerifiedEvent
     * 7. Create audit trail
     *
     * @param command VerifyApplicationCommand containing application ID and verifier details
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if verification not allowed
     */
    @Override
    public CompletableFuture<String> verifyApplication(VerifyApplicationCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Verifying application {} by compliance officer: {}",
                command.applicationId(), command.verifiedBy());

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    throw new BusinessRuleViolationException(
                        "Can only verify applications that are under review. Current status: " +
                        application.getStatus()
                    );
                }

                // Verify documents (this publishes ApplicationVerifiedEvent)
                application.verifyDocuments(command.verifiedBy());

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "APPLICATION_VERIFIED",
                    command.verifiedBy(),
                    Map.of(
                        "status", ApplicationStatus.VERIFIED.name(),
                        "verifiedAt", LocalDateTime.now().toString(),
                        "notes", command.notes() != null ? command.notes() : ""
                    )
                );

                log.info("Successfully verified application {} by officer: {}",
                    command.applicationId(), command.verifiedBy());
                return "Application verified successfully and ready for final approval";

            } catch (Exception e) {
                log.error("Failed to verify application {}", command.applicationId(), e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Requests additional information from the applicant.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is under review
     * 3. Request additional info (transitions to REQUIRES_MORE_INFO status)
     * 4. Save application
     * 5. Publish AdditionalInfoRequestedEvent
     * 6. Create audit trail
     *
     * @param applicationId UUID of the application
     * @param reason Reason for requesting additional information
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if request not allowed
     */
    public CompletableFuture<String> requestAdditionalInfo(UUID applicationId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Requesting additional info for application: {}", applicationId);

            try {
                // Validate reason
                if (reason == null || reason.trim().isEmpty()) {
                    throw new BusinessRuleViolationException("Reason is required when requesting additional information");
                }

                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", applicationId.toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    throw new BusinessRuleViolationException(
                        "Can only request additional info for applications under review. Current status: " +
                        application.getStatus()
                    );
                }

                // Request additional info (this publishes AdditionalInfoRequestedEvent)
                application.requestAdditionalInfo(reason);

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    applicationId,
                    "ADDITIONAL_INFO_REQUESTED",
                    "COMPLIANCE_OFFICER",
                    Map.of(
                        "reason", reason,
                        "requestedAt", LocalDateTime.now().toString()
                    )
                );

                log.info("Successfully requested additional info for application: {}", applicationId);
                return "Additional information requested from applicant";

            } catch (Exception e) {
                log.error("Failed to request additional info for application: {}", applicationId, e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Flags an application as suspicious.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is under review
     * 3. Flag as suspicious (transitions to FLAGGED_SUSPICIOUS status)
     * 4. Save application
     * 5. Publish ApplicationFlaggedEvent
     * 6. Create audit trail
     *
     * @param applicationId UUID of the application
     * @param reason Reason for flagging as suspicious
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if flagging not allowed
     */
    public CompletableFuture<String> flagSuspicious(UUID applicationId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            log.warn("Flagging application {} as suspicious", applicationId);

            try {
                // Validate reason
                if (reason == null || reason.trim().isEmpty()) {
                    throw new BusinessRuleViolationException("Reason is required when flagging application");
                }

                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", applicationId.toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    throw new BusinessRuleViolationException(
                        "Can only flag applications under review. Current status: " +
                        application.getStatus()
                    );
                }

                // Flag as suspicious (this publishes ApplicationFlaggedEvent)
                application.flagAsSuspicious(reason);

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    applicationId,
                    "APPLICATION_FLAGGED_SUSPICIOUS",
                    "COMPLIANCE_OFFICER",
                    Map.of(
                        "reason", reason,
                        "flaggedAt", LocalDateTime.now().toString(),
                        "requiresManualReview", true
                    )
                );

                log.warn("Successfully flagged application {} as suspicious", applicationId);
                return "Application flagged as suspicious and marked for manual review";

            } catch (Exception e) {
                log.error("Failed to flag application {} as suspicious", applicationId, e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Assigns an application to a compliance officer.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is submitted or under review
     * 3. Assign to officer
     * 4. Transition to UNDER_REVIEW if needed
     * 5. Save application
     * 6. Publish ApplicationAssignedEvent
     * 7. Create audit trail
     *
     * @param applicationId UUID of the application
     * @param officerId Email or ID of the compliance officer
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if assignment not allowed
     */
    public CompletableFuture<String> assignToSelf(UUID applicationId, String officerId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Assigning application {} to officer: {}", applicationId, officerId);

            try {
                // Validate officer ID
                if (officerId == null || officerId.trim().isEmpty()) {
                    throw new BusinessRuleViolationException("Officer ID is required");
                }

                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", applicationId.toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.SUBMITTED &&
                    application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    throw new BusinessRuleViolationException(
                        "Can only assign applications that are submitted or under review. Current status: " +
                        application.getStatus()
                    );
                }

                // Check if already assigned
                if (application.getAssignedTo() != null && !application.getAssignedTo().isEmpty()) {
                    log.warn("Application {} is already assigned to: {}",
                        applicationId, application.getAssignedTo());
                    throw new BusinessRuleViolationException(
                        "Application is already assigned to: " + application.getAssignedTo()
                    );
                }

                // Assign to officer (this publishes ApplicationAssignedEvent)
                application.assignTo(officerId);

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Create audit event
                createAuditEvent(
                    applicationId,
                    "APPLICATION_ASSIGNED",
                    officerId,
                    Map.of(
                        "assignedTo", officerId,
                        "assignedAt", LocalDateTime.now().toString(),
                        "status", application.getStatus().name()
                    )
                );

                log.info("Successfully assigned application {} to officer: {}", applicationId, officerId);
                return "Application assigned successfully to " + officerId;

            } catch (Exception e) {
                log.error("Failed to assign application {}", applicationId, e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Lists applications by status for compliance review.
     *
     * Note: This method requires repository support for querying by status.
     * In a complete implementation, the OnboardingRepository would have a method like:
     * List<OnboardingApplication> findByStatus(ApplicationStatus status)
     *
     * Current implementation returns an empty list with a note about missing repository support.
     *
     * @param status ApplicationStatus to filter by
     * @return CompletableFuture with list of applications
     */
    public CompletableFuture<List<OnboardingApplication>> listApplicationsByStatus(ApplicationStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Listing applications with status: {}", status);

            try {
                // Find applications by status
                List<OnboardingApplication> applications = onboardingRepository.findAllByStatus(status.name());

                // Create audit event for the query
                createAuditEvent(
                    null,
                    "APPLICATIONS_QUERIED_BY_STATUS",
                    "COMPLIANCE_OFFICER",
                    Map.of(
                        "status", status.name(),
                        "count", applications.size(),
                        "queriedAt", LocalDateTime.now().toString()
                    )
                );

                log.info("Retrieved {} applications with status: {}", applications.size(), status);
                return applications;

            } catch (Exception e) {
                log.error("Failed to list applications by status: {}", status, e);
                throw e;
            }
        }, asyncExecutor);
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
}
