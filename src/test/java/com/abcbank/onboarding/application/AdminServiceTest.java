package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.in.ApproveApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.RejectApplicationUseCase;
import com.abcbank.onboarding.domain.port.out.*;
import com.abcbank.onboarding.domain.service.AccountNumberGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminService.
 * Tests all admin operations including corner cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock
    private OnboardingRepository onboardingRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountNumberGeneratorService accountNumberGeneratorService;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private NotificationService notificationService;

    private AdminService adminService;

    private OnboardingApplication testApplication;
    private UUID testApplicationId;
    private Executor testExecutor;

    @BeforeEach
    void setUp() {
        testExecutor = Executors.newSingleThreadExecutor();
        adminService = new AdminService(
                onboardingRepository,
                customerRepository,
                accountNumberGeneratorService,
                eventPublisher,
                auditRepository,
                notificationService,
                testExecutor
        );

        testApplicationId = UUID.randomUUID();
        Address address = new Address("Main St", "123", "1234AB", "Amsterdam", "NL");
        testApplication = new OnboardingApplication(
                testApplicationId,
                "John",
                "Doe",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "+31612345678",
                "john.doe@example.com",
                "NL",
                address,
                "123456789"
        );

        // Add required consents
        testApplication.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.DATA_PROCESSING,
                true,
                "127.0.0.1",
                "I consent",
                "1.0"
        ));
        testApplication.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.TERMS_AND_CONDITIONS,
                true,
                "127.0.0.1",
                "I accept",
                "1.0"
        ));

        // Add documents and verify
        testApplication.markEmailAsVerified();
        testApplication.addDocument(new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PASSPORT,
                "/path/passport.jpg",
                "image/jpeg",
                1024L
        ));
        testApplication.addDocument(new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PHOTO,
                "/path/photo.jpg",
                "image/jpeg",
                1024L
        ));

        testApplication.submit();
        testApplication.assignTo("officer@abc.nl");
        testApplication.verifyDocuments("officer@abc.nl");
    }

    // ==================== approveApplication() Tests ====================

    @Test
    @DisplayName("Should successfully approve verified application")
    void shouldApproveVerifiedApplication() throws Exception {
        // Given
        ApproveApplicationUseCase.ApproveApplicationCommand command =
                new ApproveApplicationUseCase.ApproveApplicationCommand(
                        testApplicationId,
                        "admin@abc.nl",
                        "Looks good"
                );

        String accountNumber = "NL91ABCD0417164300";
        Customer mockCustomer = new Customer(
                UUID.randomUUID(),
                "CUST-20251103-123",
                accountNumber,
                testApplicationId,
                "John",
                "Doe",
                "john.doe@example.com",
                "+31612345678"
        );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(accountNumberGeneratorService.ensureUnique(customerRepository)).thenReturn(accountNumber);
        when(customerRepository.save(any(Customer.class))).thenReturn(mockCustomer);
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(notificationService).sendApprovalNotification(any(), any(), any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = adminService.approveApplication(command);
        String returnedAccountNumber = result.get();

        // Then
        assertThat(returnedAccountNumber).isNotNull();
        assertThat(returnedAccountNumber).isEqualTo(accountNumber);
        assertThat(returnedAccountNumber).startsWith("NL");
        assertThat(returnedAccountNumber).hasSize(18);

        verify(onboardingRepository).findById(testApplicationId);
        verify(accountNumberGeneratorService).ensureUnique(customerRepository);
        verify(customerRepository).save(any(Customer.class));
        verify(onboardingRepository).save(testApplication);
        verify(eventPublisher, atLeastOnce()).publish(any());
        verify(notificationService).sendApprovalNotification(eq(testApplicationId), any(), eq(accountNumber));
    }

    @Test
    @DisplayName("Should successfully approve flagged suspicious application")
    void shouldApproveFlaggedApplication() throws Exception {
        // Given
        // Create application in UNDER_REVIEW state first (before verifying)
        OnboardingApplication flaggedApp = new OnboardingApplication(
                testApplicationId,
                "John",
                "Doe",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "+31612345678",
                "john.doe@example.com",
                "NL",
                new Address("Main St", "123", "1234AB", "Amsterdam", "NL"),
                "123456789"
        );

        // Add required consents
        flaggedApp.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.DATA_PROCESSING,
                true,
                "127.0.0.1",
                "I consent",
                "1.0"
        ));
        flaggedApp.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.TERMS_AND_CONDITIONS,
                true,
                "127.0.0.1",
                "I accept",
                "1.0"
        ));

        // Add documents and verify email
        flaggedApp.markEmailAsVerified();
        flaggedApp.addDocument(new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PASSPORT,
                "/path/passport.jpg",
                "image/jpeg",
                1024L
        ));
        flaggedApp.addDocument(new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PHOTO,
                "/path/photo.jpg",
                "image/jpeg",
                1024L
        ));

        flaggedApp.submit();
        flaggedApp.assignTo("officer@abc.nl");

        // Flag as suspicious (must be done while UNDER_REVIEW)
        flaggedApp.flagAsSuspicious("Needs review");

        ApproveApplicationUseCase.ApproveApplicationCommand command =
                new ApproveApplicationUseCase.ApproveApplicationCommand(
                        testApplicationId,
                        "admin@abc.nl",
                        "Reviewed and approved"
                );

        String accountNumber = "NL91ABCD0417164300";
        Customer mockCustomer = new Customer(
                UUID.randomUUID(),
                "CUST-20251103-456",
                accountNumber,
                testApplicationId,
                "John",
                "Doe",
                "john.doe@example.com",
                "+31612345678"
        );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(flaggedApp));
        when(accountNumberGeneratorService.ensureUnique(customerRepository)).thenReturn(accountNumber);
        when(customerRepository.save(any(Customer.class))).thenReturn(mockCustomer);
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(flaggedApp);
        doNothing().when(notificationService).sendApprovalNotification(any(), any(), any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = adminService.approveApplication(command);
        String returnedAccountNumber = result.get();

        // Then
        assertThat(returnedAccountNumber).isNotNull();
        assertThat(flaggedApp.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

        verify(onboardingRepository).findById(testApplicationId);
        verify(onboardingRepository).save(flaggedApp);
    }

    @Test
    @DisplayName("Should throw exception when application not found")
    void shouldThrowExceptionWhenApplicationNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        ApproveApplicationUseCase.ApproveApplicationCommand command =
                new ApproveApplicationUseCase.ApproveApplicationCommand(
                        nonExistentId,
                        "admin@abc.nl",
                        null
                );

        when(onboardingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> adminService.approveApplication(command).join())
                .hasCauseInstanceOf(ResourceNotFoundException.class);

        verify(onboardingRepository).findById(nonExistentId);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate unique account numbers for multiple approvals")
    void shouldGenerateUniqueAccountNumbers() throws Exception {
        // Given
        ApproveApplicationUseCase.ApproveApplicationCommand command1 =
                new ApproveApplicationUseCase.ApproveApplicationCommand(
                        testApplicationId,
                        "admin@abc.nl",
                        null
                );

        String accountNumber1 = "NL91ABCD0417164300";
        Customer mockCustomer1 = new Customer(
                UUID.randomUUID(),
                "CUST-20251103-111",
                accountNumber1,
                testApplicationId,
                "John",
                "Doe",
                "john.doe@example.com",
                "+31612345678"
        );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(accountNumberGeneratorService.ensureUnique(customerRepository))
                .thenReturn(accountNumber1);
        when(customerRepository.save(any(Customer.class))).thenReturn(mockCustomer1);
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(notificationService).sendApprovalNotification(any(), any(), any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When - Approve first application
        String returnedAccountNumber1 = adminService.approveApplication(command1).get();

        // Setup second application
        UUID testApplicationId2 = UUID.randomUUID();
        OnboardingApplication testApplication2 = new OnboardingApplication(
                testApplicationId2,
                "Jane",
                "Doe",
                Gender.FEMALE,
                LocalDate.of(1992, 1, 1),
                "+31687654321",
                "jane.doe@example.com",
                "NL",
                new Address("Main St", "456", "5678CD", "Rotterdam", "NL"),
                "987654321"
        );
        testApplication2.addConsent(new ConsentRecord(
                UUID.randomUUID(), ConsentType.DATA_PROCESSING, true, "127.0.0.1", "I consent", "1.0"));
        testApplication2.addConsent(new ConsentRecord(
                UUID.randomUUID(), ConsentType.TERMS_AND_CONDITIONS, true, "127.0.0.1", "I accept", "1.0"));
        testApplication2.markEmailAsVerified();
        testApplication2.addDocument(new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT, "/path", "image/jpeg", 1024L));
        testApplication2.addDocument(new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PHOTO, "/path", "image/jpeg", 1024L));
        testApplication2.submit();
        testApplication2.assignTo("officer@abc.nl");
        testApplication2.verifyDocuments("officer@abc.nl");

        ApproveApplicationUseCase.ApproveApplicationCommand command2 =
                new ApproveApplicationUseCase.ApproveApplicationCommand(
                        testApplicationId2,
                        "admin@abc.nl",
                        null
                );

        String accountNumber2 = "NL42ABCD0517264400";
        Customer mockCustomer2 = new Customer(
                UUID.randomUUID(),
                "CUST-20251103-222",
                accountNumber2,
                testApplicationId2,
                "Jane",
                "Doe",
                "jane.doe@example.com",
                "+31687654321"
        );

        when(onboardingRepository.findById(testApplicationId2)).thenReturn(Optional.of(testApplication2));
        when(accountNumberGeneratorService.ensureUnique(customerRepository))
                .thenReturn(accountNumber2);
        when(customerRepository.save(any(Customer.class))).thenReturn(mockCustomer2);
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication2);

        // When - Approve second application
        String returnedAccountNumber2 = adminService.approveApplication(command2).get();

        // Then
        assertThat(returnedAccountNumber1).isNotEqualTo(returnedAccountNumber2);
        assertThat(returnedAccountNumber1).startsWith("NL");
        assertThat(returnedAccountNumber2).startsWith("NL");
    }

    // ==================== rejectApplication() Tests ====================

    @Test
    @DisplayName("Should successfully reject verified application")
    void shouldRejectVerifiedApplication() throws Exception {
        // Given
        RejectApplicationUseCase.RejectApplicationCommand command =
                new RejectApplicationUseCase.RejectApplicationCommand(
                        testApplicationId,
                        "admin@abc.nl",
                        "Document verification failed"
                );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(notificationService).sendRejectionNotification(any(), any(), any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = adminService.rejectApplication(command);
        String message = result.get();

        // Then
        assertThat(message).contains("rejected successfully");
        assertThat(testApplication.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(testApplication.getRejectionReason()).isEqualTo("Document verification failed");

        verify(onboardingRepository).findById(testApplicationId);
        verify(onboardingRepository).save(testApplication);
        verify(eventPublisher, atLeastOnce()).publish(any());
        verify(notificationService).sendRejectionNotification(eq(testApplicationId), any(), eq("Document verification failed"));
    }

    @Test
    @DisplayName("Should successfully reject under review application")
    void shouldRejectUnderReviewApplication() throws Exception {
        // Given
        // Reset to under review status
        testApplication = new OnboardingApplication(
                testApplicationId,
                "John",
                "Doe",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                "+31612345678",
                "john.doe@example.com",
                "NL",
                new Address("Main St", "123", "1234AB", "Amsterdam", "NL"),
                "123456789"
        );
        testApplication.addConsent(new ConsentRecord(
                UUID.randomUUID(), ConsentType.DATA_PROCESSING, true, "127.0.0.1", "I consent", "1.0"));
        testApplication.addConsent(new ConsentRecord(
                UUID.randomUUID(), ConsentType.TERMS_AND_CONDITIONS, true, "127.0.0.1", "I accept", "1.0"));
        testApplication.markEmailAsVerified();
        testApplication.addDocument(new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT, "/path", "image/jpeg", 1024L));
        testApplication.addDocument(new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PHOTO, "/path", "image/jpeg", 1024L));
        testApplication.submit();
        testApplication.assignTo("officer@abc.nl");

        RejectApplicationUseCase.RejectApplicationCommand command =
                new RejectApplicationUseCase.RejectApplicationCommand(
                        testApplicationId,
                        "admin@abc.nl",
                        "Failed background check"
                );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(notificationService).sendRejectionNotification(any(), any(), any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = adminService.rejectApplication(command);
        result.get();

        // Then
        assertThat(testApplication.getStatus()).isEqualTo(ApplicationStatus.REJECTED);

        verify(onboardingRepository).findById(testApplicationId);
        verify(onboardingRepository).save(testApplication);
    }

    @Test
    @DisplayName("Should throw exception when rejecting non-existent application")
    void shouldThrowExceptionWhenRejectingNonExistentApplication() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        RejectApplicationUseCase.RejectApplicationCommand command =
                new RejectApplicationUseCase.RejectApplicationCommand(
                        nonExistentId,
                        "admin@abc.nl",
                        "Reason"
                );

        when(onboardingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> adminService.rejectApplication(command).join())
                .hasCauseInstanceOf(ResourceNotFoundException.class);

        verify(onboardingRepository).findById(nonExistentId);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should set data retention date when rejecting")
    void shouldSetDataRetentionDateWhenRejecting() throws Exception {
        // Given
        RejectApplicationUseCase.RejectApplicationCommand command =
                new RejectApplicationUseCase.RejectApplicationCommand(
                        testApplicationId,
                        "admin@abc.nl",
                        "Incomplete information"
                );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(notificationService).sendRejectionNotification(any(), any(), any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        adminService.rejectApplication(command).get();

        // Then
        assertThat(testApplication.getDataRetentionUntil()).isNotNull();
        assertThat(testApplication.getDataRetentionUntil()).isAfter(testApplication.getRejectedAt());
    }

    // ==================== getApplicationMetrics() Tests ====================

    @Test
    @DisplayName("Should return metrics structure (stub implementation)")
    void shouldReturnMetricsStructure() throws Exception {
        // Given
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<Map<String, Object>> result = adminService.getApplicationMetrics();
        Map<String, Object> metrics = result.get();

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics).containsKeys("total", "approved", "rejected", "pending", "submitted",
                "underReview", "verified", "flagged", "requiresMoreInfo", "generatedAt");
        // Note: Currently returns zero values as metrics queries are not fully implemented
    }
}
