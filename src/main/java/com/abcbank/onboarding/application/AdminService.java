package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.event.CustomerCreatedEvent;
import com.abcbank.onboarding.domain.event.DomainEvent;
import com.abcbank.onboarding.domain.exception.BusinessRuleViolationException;
import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.in.ApproveApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.RejectApplicationUseCase;
import com.abcbank.onboarding.domain.port.out.*;
import com.abcbank.onboarding.domain.service.AccountNumberGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Service for administrator operations.
 *
 * Responsibilities:
 * - Approve verified applications
 * - Reject applications with reasons
 * - Create customer accounts
 * - Generate account numbers
 * - Send approval/rejection notifications
 * - Provide application metrics
 *
 * This service implements the final approval workflow and customer account creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminService implements ApproveApplicationUseCase, RejectApplicationUseCase {

    private final OnboardingRepository onboardingRepository;
    private final CustomerRepository customerRepository;
    private final AccountNumberGeneratorService accountNumberGeneratorService;
    private final EventPublisher eventPublisher;
    private final AuditRepository auditRepository;
    private final NotificationService notificationService;

    @Qualifier("asyncExecutor")
    private final Executor asyncExecutor;

    /**
     * Approves an application and creates customer account.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application is verified or flagged (with admin override)
     * 3. Generate unique account number
     * 4. Create Customer entity
     * 5. Save customer
     * 6. Approve application (transitions to APPROVED status)
     * 7. Save application
     * 8. Publish CustomerCreatedEvent and ApplicationApprovedEvent
     * 9. Send approval notification
     * 10. Create audit trail
     *
     * @param command ApproveApplicationCommand containing approval details
     * @return CompletableFuture with account number
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if approval not allowed
     */
    @Override
    public CompletableFuture<String> approveApplication(ApproveApplicationCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Approving application {} by admin: {}",
                command.applicationId(), command.approvedBy());

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.VERIFIED &&
                    application.getStatus() != ApplicationStatus.FLAGGED_SUSPICIOUS) {
                    throw new BusinessRuleViolationException(
                        "Can only approve verified or flagged applications. Current status: " +
                        application.getStatus()
                    );
                }

                // Generate unique account number
                String accountNumber = accountNumberGeneratorService.ensureUnique(customerRepository);

                // Generate customer ID
                String customerId = generateCustomerId();

                // Create Customer entity
                UUID customerUuid = UUID.randomUUID();
                Customer customer = new Customer(
                    customerUuid,
                    customerId,
                    accountNumber,
                    command.applicationId(),
                    application.getFirstName(),
                    application.getLastName(),
                    application.getEmail(),
                    application.getPhone()
                );

                // Save customer
                Customer savedCustomer = customerRepository.save(customer);

                // Approve application (this publishes ApplicationApprovedEvent)
                application.approve(customerUuid, accountNumber, command.approvedBy());

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish CustomerCreatedEvent
                eventPublisher.publish(new CustomerCreatedEvent(
                    customerUuid,
                    command.applicationId(),
                    accountNumber
                ));

                // Publish domain events from application
                publishDomainEvents(savedApplication);

                // Send approval notification
                notificationService.sendApprovalNotification(
                    command.applicationId(),
                    application.getEmail(),
                    accountNumber
                );

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "APPLICATION_APPROVED",
                    command.approvedBy(),
                    Map.of(
                        "customerId", customerId,
                        "accountNumber", accountNumber,
                        "customerUuid", customerUuid.toString(),
                        "approvedAt", LocalDateTime.now().toString(),
                        "approvalNotes", command.approvalNotes() != null ? command.approvalNotes() : ""
                    )
                );

                log.info("Successfully approved application {} and created customer account: {}",
                    command.applicationId(), accountNumber);
                return accountNumber;

            } catch (Exception e) {
                log.error("Failed to approve application {}", command.applicationId(), e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Rejects an application with a reason.
     *
     * Process:
     * 1. Validate application exists
     * 2. Validate application can be rejected
     * 3. Reject application (transitions to REJECTED status)
     * 4. Save application
     * 5. Publish ApplicationRejectedEvent
     * 6. Send rejection notification
     * 7. Create audit trail
     *
     * @param command RejectApplicationCommand containing rejection details
     * @return CompletableFuture with success message
     * @throws ResourceNotFoundException if application not found
     * @throws BusinessRuleViolationException if rejection not allowed
     */
    @Override
    public CompletableFuture<String> rejectApplication(RejectApplicationCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Rejecting application {} by admin: {}",
                command.applicationId(), command.rejectedBy());

            try {
                // Validate rejection reason
                if (command.rejectionReason() == null || command.rejectionReason().trim().isEmpty()) {
                    throw new BusinessRuleViolationException("Rejection reason is required");
                }

                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(command.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", command.applicationId().toString()
                    ));

                // Validate application status
                if (application.getStatus() != ApplicationStatus.VERIFIED &&
                    application.getStatus() != ApplicationStatus.FLAGGED_SUSPICIOUS &&
                    application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    throw new BusinessRuleViolationException(
                        "Can only reject applications under review, verified, or flagged. Current status: " +
                        application.getStatus()
                    );
                }

                // Reject application (this publishes ApplicationRejectedEvent)
                application.reject(command.rejectionReason());

                // Save application
                OnboardingApplication savedApplication = onboardingRepository.save(application);

                // Publish domain events
                publishDomainEvents(savedApplication);

                // Send rejection notification
                notificationService.sendRejectionNotification(
                    command.applicationId(),
                    application.getEmail(),
                    command.rejectionReason()
                );

                // Create audit event
                createAuditEvent(
                    command.applicationId(),
                    "APPLICATION_REJECTED",
                    command.rejectedBy(),
                    Map.of(
                        "rejectionReason", command.rejectionReason(),
                        "rejectedAt", LocalDateTime.now().toString(),
                        "rejectedBy", command.rejectedBy()
                    )
                );

                log.info("Successfully rejected application {}", command.applicationId());
                return "Application rejected successfully";

            } catch (Exception e) {
                log.error("Failed to reject application {}", command.applicationId(), e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Retrieves application metrics for dashboard.
     *
     * Note: This is a simplified implementation. In production, this would query
     * the database for actual counts using repository methods like:
     * - countByStatus(ApplicationStatus status)
     * - countAll()
     *
     * Current implementation returns mock metrics with a note.
     *
     * @return CompletableFuture with application metrics
     */
    public CompletableFuture<Map<String, Object>> getApplicationMetrics() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Retrieving application metrics");

            try {
                // Get actual metrics from repository
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("total", onboardingRepository.count());
                metrics.put("initiated", onboardingRepository.countByStatus(ApplicationStatus.INITIATED.name()));
                metrics.put("otpVerified", onboardingRepository.countByStatus(ApplicationStatus.OTP_VERIFIED.name()));
                metrics.put("documentsUploaded", onboardingRepository.countByStatus(ApplicationStatus.DOCUMENTS_UPLOADED.name()));
                metrics.put("submitted", onboardingRepository.countByStatus(ApplicationStatus.SUBMITTED.name()));
                metrics.put("underReview", onboardingRepository.countByStatus(ApplicationStatus.UNDER_REVIEW.name()));
                metrics.put("requiresMoreInfo", onboardingRepository.countByStatus(ApplicationStatus.REQUIRES_MORE_INFO.name()));
                metrics.put("verified", onboardingRepository.countByStatus(ApplicationStatus.VERIFIED.name()));
                metrics.put("flagged", onboardingRepository.countByStatus(ApplicationStatus.FLAGGED_SUSPICIOUS.name()));
                metrics.put("approved", onboardingRepository.countByStatus(ApplicationStatus.APPROVED.name()));
                metrics.put("rejected", onboardingRepository.countByStatus(ApplicationStatus.REJECTED.name()));
                metrics.put("generatedAt", LocalDateTime.now().toString());

                // Create audit event
                createAuditEvent(
                    null,
                    "METRICS_RETRIEVED",
                    "ADMIN",
                    Map.of("retrievedAt", LocalDateTime.now().toString())
                );

                log.info("Successfully retrieved application metrics");
                return metrics;

            } catch (Exception e) {
                log.error("Failed to retrieve application metrics", e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Generates a unique customer ID.
     * Format: CUST-YYYYMMDD-###
     *
     * In production, this would ensure uniqueness by checking against existing customers.
     */
    private String generateCustomerId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String datePart = LocalDateTime.now().format(formatter);

        // Generate random 3-digit number
        int randomNum = (int) (Math.random() * 900) + 100; // 100-999

        return String.format("CUST-%s-%03d", datePart, randomNum);
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
