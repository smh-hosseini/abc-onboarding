package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.event.DomainEvent;
import com.abcbank.onboarding.domain.event.OtpSentEvent;
import com.abcbank.onboarding.domain.exception.BusinessRuleViolationException;
import com.abcbank.onboarding.domain.exception.OtpExpiredException;
import com.abcbank.onboarding.domain.exception.OtpMaxAttemptsException;
import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.AuditEvent;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.model.OtpVerification;
import com.abcbank.onboarding.domain.port.in.VerifyOtpUseCase;
import com.abcbank.onboarding.domain.port.out.AuditRepository;
import com.abcbank.onboarding.domain.port.out.EventPublisher;
import com.abcbank.onboarding.domain.port.out.NotificationService;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import com.abcbank.onboarding.domain.port.out.OtpVerificationRepository;
import com.abcbank.onboarding.domain.service.OtpService;
import com.abcbank.onboarding.infrastructure.security.JwtTokenService;
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

/**
 * Service for handling OTP (One-Time Password) operations.
 *
 * Responsibilities:
 * - Send OTP to applicants via SMS/Email
 * - Verify OTP codes
 * - Manage OTP expiry and attempt limits per channel
 * - Generate JWT tokens after successful verification
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = BusinessRuleViolationException.class)
public class OtpApplicationService implements VerifyOtpUseCase {

    private static final int MAX_OTP_ATTEMPTS = 3;
    private static final int OTP_EXPIRY_SECONDS = 600; // 10 minutes

    private final OnboardingRepository onboardingRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final OtpService otpService;
    private final JwtTokenService jwtTokenService;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final AuditRepository auditRepository;

    @Qualifier("asyncExecutor")
    private final Executor asyncExecutor;

    /**
     * Sends OTP to the applicant via specified channel.
     *
     * Process:
     * 1. Validate application exists
     * 2. Generate new OTP
     * 3. Hash OTP for secure storage
     * 4. Save OTP to otp_verification table with channel
     * 5. Send OTP via specified channel (SMS or Email)
     * 6. Publish OtpSentEvent
     * 7. Create audit trail
     *
     * @param applicationId UUID of the application
     * @param channel Channel to send OTP through (EMAIL or SMS)
     * @return CompletableFuture with OTP expiry time in seconds
     * @throws ResourceNotFoundException if application not found
     */
    public CompletableFuture<Integer> sendOtp(UUID applicationId, OtpVerification.OtpChannel channel) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Sending OTP for application: {} via channel: {}", applicationId, channel);

            try {
                // Find application
                OnboardingApplication application = onboardingRepository
                    .findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Application", applicationId.toString()
                    ));

                // Generate OTP
                String otp = otpService.generateOtp();
                String otpHash = otpService.hashOtp(otp);
                LocalDateTime otpExpiry = otpService.generateOtpExpiryTime();

                // Create OTP verification record
                OtpVerification otpVerification = new OtpVerification(
                    UUID.randomUUID(),
                    applicationId,
                    channel,
                    otpHash,
                    otpExpiry
                );

                // Save OTP verification
                otpVerificationRepository.save(otpVerification);

                // Send OTP via specified channel
                String contactInfo;
                if (channel == OtpVerification.OtpChannel.EMAIL) {
                    notificationService.sendOtpEmail(application.getEmail(), otp);
                    contactInfo = maskContact(application.getEmail());
                } else {
                    notificationService.sendOtpSms(application.getPhone(), otp);
                    contactInfo = maskContact(application.getPhone());
                }

                // Publish OtpSentEvent
                eventPublisher.publish(new OtpSentEvent(
                    applicationId,
                    contactInfo,
                    channel.name()
                ));

                // Create audit event
                createAuditEvent(
                    applicationId,
                    "OTP_SENT",
                    "SYSTEM",
                    Map.of(
                        "channel", channel.name(),
                        "contact", contactInfo,
                        "expiresAt", otpExpiry.toString()
                    )
                );

                log.info("Successfully sent OTP for application: {} via {}", applicationId, channel);
                return OTP_EXPIRY_SECONDS;

            } catch (Exception e) {
                log.error("Failed to send OTP for application: {}", applicationId, e);
                throw e;
            }
        }, asyncExecutor);
    }

    /**
     * Verifies OTP code for a specific channel.
     *
     * Process:
     * 1. Validate application exists
     * 2. Find pending OTP for the channel
     * 3. Check OTP expiry
     * 4. Check attempt limits
     * 5. Verify OTP against stored hash
     * 6. Mark channel as verified in application
     * 7. Update application status if needed
     * 8. Publish OtpVerifiedEvent
     * 9. Create audit trail
     * 10. Generate and return JWT token
     *
     * @param command VerifyOtpCommand containing application ID, OTP, and channel
     * @return JWT token string for authenticated session
     * @throws ResourceNotFoundException if application not found
     * @throws OtpExpiredException if OTP has expired
     * @throws OtpMaxAttemptsException if max attempts exceeded
     * @throws BusinessRuleViolationException if OTP is invalid
     */
    @Override
    public String execute(VerifyOtpCommand command) {
        log.info("Verifying OTP for application: {} on channel: {}", command.applicationId(), command.channel());

        try {
            // Find application
            OnboardingApplication application = onboardingRepository
                .findById(command.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Application", command.applicationId().toString()
                ));

            // Find pending OTP verification for this channel
            OtpVerification otpVerification = otpVerificationRepository
                .findLatestByApplicationIdAndChannel(command.applicationId(), command.channel())
                .orElseThrow(() -> new BusinessRuleViolationException(
                    "No pending OTP found for this channel. Please request a new OTP."
                ));

            // Check OTP expiry
            if (otpVerification.isExpired()) {
                otpVerification.markAsExpired();
                otpVerificationRepository.save(otpVerification);

                createAuditEvent(
                    command.applicationId(),
                    "OTP_VERIFICATION_FAILED",
                    "APPLICANT",
                    Map.of("reason", "OTP expired", "channel", command.channel().name())
                );
                throw new OtpExpiredException("OTP has expired. Please request a new one.");
            }

            // Check attempt limits
            if (otpVerification.getAttempts() >= MAX_OTP_ATTEMPTS) {
                otpVerification.markAsMaxAttemptsExceeded();
                otpVerificationRepository.save(otpVerification);

                createAuditEvent(
                    command.applicationId(),
                    "OTP_VERIFICATION_FAILED",
                    "APPLICANT",
                    Map.of("reason", "Max attempts exceeded", "channel", command.channel().name())
                );
                throw new OtpMaxAttemptsException(
                    "Maximum OTP verification attempts exceeded. Please request a new OTP."
                );
            }

            // Verify OTP
            boolean isValid = otpService.verifyOtp(command.otp(), otpVerification.getOtpHash());

            if (!isValid) {
                // Increment attempts
                otpVerification.incrementAttempts();
                otpVerificationRepository.save(otpVerification);

                createAuditEvent(
                    command.applicationId(),
                    "OTP_VERIFICATION_FAILED",
                    "APPLICANT",
                    Map.of(
                        "reason", "Invalid OTP",
                        "attempts", otpVerification.getAttempts(),
                        "channel", command.channel().name()
                    )
                );

                int remainingAttempts = MAX_OTP_ATTEMPTS - otpVerification.getAttempts();
                throw new BusinessRuleViolationException(
                    "Invalid OTP. " + remainingAttempts + " attempt(s) remaining."
                );
            }

            // OTP is valid - mark as verified
            otpVerification.markAsVerified();
            otpVerificationRepository.save(otpVerification);

            // Mark the channel as verified in the application
            if (command.channel() == OtpVerification.OtpChannel.EMAIL) {
                application.markEmailAsVerified();
            } else {
                application.markPhoneAsVerified();
            }

            // Save application
            OnboardingApplication savedApplication = onboardingRepository.save(application);

            // Publish domain events
            publishDomainEvents(savedApplication);

            // Create audit event
            createAuditEvent(
                command.applicationId(),
                "OTP_VERIFIED",
                "APPLICANT",
                Map.of(
                    "verifiedAt", LocalDateTime.now().toString(),
                    "channel", command.channel().name()
                )
            );

            // Generate JWT token
            String jwtToken = generateJwtToken(command.applicationId());

            log.info("Successfully verified OTP for application: {} on channel: {}",
                    command.applicationId(), command.channel());
            return jwtToken;

        } catch (Exception e) {
            log.error("Failed to verify OTP for application: {}", command.applicationId(), e);
            throw e;
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
     * Masks contact information for logging (GDPR compliance).
     */
    private String maskContact(String contact) {
        if (contact == null || contact.length() < 4) {
            return "****";
        }
        if (contact.contains("@")) {
            // Email masking
            String[] parts = contact.split("@");
            return parts[0].substring(0, 2) + "***@" + parts[1];
        } else {
            // Phone masking
            return contact.substring(0, 3) + "****" + contact.substring(contact.length() - 2);
        }
    }

    /**
     * Generates JWT token for authenticated session.
     */
    private String generateJwtToken(UUID applicationId) {
        return jwtTokenService.generateApplicantToken(applicationId);
    }
}
