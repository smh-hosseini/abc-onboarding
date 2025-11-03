package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.exception.BusinessRuleViolationException;
import com.abcbank.onboarding.domain.exception.ResourceNotFoundException;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.in.VerifyApplicationUseCase;
import com.abcbank.onboarding.domain.port.out.AuditRepository;
import com.abcbank.onboarding.domain.port.out.EventPublisher;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ComplianceService.
 * Tests all compliance operations including corner cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceService Unit Tests")
class ComplianceServiceTest {

    @Mock
    private OnboardingRepository onboardingRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AuditRepository auditRepository;

    private ComplianceService complianceService;

    private OnboardingApplication testApplication;
    private UUID testApplicationId;
    private Executor testExecutor;

    @BeforeEach
    void setUp() {
        testExecutor = Executors.newSingleThreadExecutor();
        complianceService = new ComplianceService(
                onboardingRepository,
                eventPublisher,
                auditRepository,
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
    }

    // ==================== verifyApplication() Tests ====================

    @Test
    @DisplayName("Should successfully verify application under review")
    void shouldVerifyApplicationUnderReview() throws Exception {
        // Given
        VerifyApplicationUseCase.VerifyApplicationCommand command =
                new VerifyApplicationUseCase.VerifyApplicationCommand(
                        testApplicationId,
                        "officer@abc.nl",
                        "All documents verified"
                );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(eventPublisher).publish(any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = complianceService.verifyApplication(command);
        String message = result.get();

        // Then
        assertThat(message).contains("verified successfully");
        assertThat(testApplication.getStatus()).isEqualTo(ApplicationStatus.VERIFIED);

        verify(onboardingRepository).findById(testApplicationId);
        verify(onboardingRepository).save(testApplication);
        verify(eventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("Should throw exception when verifying non-existent application")
    void shouldThrowExceptionWhenVerifyingNonExistentApplication() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        VerifyApplicationUseCase.VerifyApplicationCommand command =
                new VerifyApplicationUseCase.VerifyApplicationCommand(
                        nonExistentId,
                        "officer@abc.nl",
                        null
                );

        when(onboardingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> complianceService.verifyApplication(command).join())
                .hasCauseInstanceOf(ResourceNotFoundException.class);

        verify(onboardingRepository).findById(nonExistentId);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when verifying application not under review")
    void shouldThrowExceptionWhenVerifyingWrongStatus() {
        // Given - Create a DRAFT application
        OnboardingApplication draftApplication = new OnboardingApplication(
                testApplicationId,
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

        VerifyApplicationUseCase.VerifyApplicationCommand command =
                new VerifyApplicationUseCase.VerifyApplicationCommand(
                        testApplicationId,
                        "officer@abc.nl",
                        null
                );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(draftApplication));

        // When / Then
        assertThatThrownBy(() -> complianceService.verifyApplication(command).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Can only verify applications that are under review");

        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should transition to VERIFIED status when verifying")
    void shouldTransitionToVerifiedStatus() throws Exception {
        // Given
        VerifyApplicationUseCase.VerifyApplicationCommand command =
                new VerifyApplicationUseCase.VerifyApplicationCommand(
                        testApplicationId,
                        "officer@abc.nl",
                        null
                );

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(eventPublisher).publish(any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        complianceService.verifyApplication(command).get();

        // Then
        assertThat(testApplication.getStatus()).isEqualTo(ApplicationStatus.VERIFIED);
        verify(onboardingRepository).save(testApplication);
    }

    // ==================== requestAdditionalInfo() Tests ====================

    @Test
    @DisplayName("Should successfully request additional information")
    void shouldRequestAdditionalInformation() throws Exception {
        // Given
        UUID applicationId = testApplicationId;
        String reason = "Need proof of address";

        when(onboardingRepository.findById(applicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(eventPublisher).publish(any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = complianceService.requestAdditionalInfo(applicationId, reason);
        String message = result.get();

        // Then
        assertThat(message).contains("Additional information requested");
        assertThat(testApplication.getStatus()).isEqualTo(ApplicationStatus.REQUIRES_MORE_INFO);

        verify(onboardingRepository).findById(applicationId);
        verify(onboardingRepository).save(testApplication);
        verify(eventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("Should throw exception when requesting info with empty reason")
    void shouldThrowExceptionWhenRequestingInfoWithEmptyReason() {
        // Given
        UUID applicationId = testApplicationId;
        String emptyReason = "";

        // When / Then
        assertThatThrownBy(() -> complianceService.requestAdditionalInfo(applicationId, emptyReason).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Reason is required");

        verify(onboardingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw exception when requesting info for non-existent application")
    void shouldThrowExceptionWhenRequestingInfoForNonExistentApplication() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        String reason = "Need more info";

        when(onboardingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> complianceService.requestAdditionalInfo(nonExistentId, reason).join())
                .hasCauseInstanceOf(ResourceNotFoundException.class);

        verify(onboardingRepository).findById(nonExistentId);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when requesting info for wrong status")
    void shouldThrowExceptionWhenRequestingInfoForWrongStatus() {
        // Given - Create a DRAFT application
        OnboardingApplication draftApplication = new OnboardingApplication(
                testApplicationId,
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

        String reason = "Need more info";
        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(draftApplication));

        // When / Then
        assertThatThrownBy(() -> complianceService.requestAdditionalInfo(testApplicationId, reason).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Can only request additional info for applications under review");

        verify(onboardingRepository, never()).save(any());
    }

    // ==================== flagSuspicious() Tests ====================

    @Test
    @DisplayName("Should successfully flag application as suspicious")
    void shouldFlagApplicationAsSuspicious() throws Exception {
        // Given
        UUID applicationId = testApplicationId;
        String reason = "Suspicious transaction history";

        when(onboardingRepository.findById(applicationId)).thenReturn(Optional.of(testApplication));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(testApplication);
        doNothing().when(eventPublisher).publish(any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = complianceService.flagSuspicious(applicationId, reason);
        String message = result.get();

        // Then
        assertThat(message).contains("flagged as suspicious");
        assertThat(testApplication.getStatus()).isEqualTo(ApplicationStatus.FLAGGED_SUSPICIOUS);

        verify(onboardingRepository).findById(applicationId);
        verify(onboardingRepository).save(testApplication);
        verify(eventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("Should throw exception when flagging with empty reason")
    void shouldThrowExceptionWhenFlaggingWithEmptyReason() {
        // Given
        UUID applicationId = testApplicationId;
        String emptyReason = "";

        // When / Then
        assertThatThrownBy(() -> complianceService.flagSuspicious(applicationId, emptyReason).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Reason is required");

        verify(onboardingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw exception when flagging non-existent application")
    void shouldThrowExceptionWhenFlaggingNonExistentApplication() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        String reason = "Suspicious activity";

        when(onboardingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> complianceService.flagSuspicious(nonExistentId, reason).join())
                .hasCauseInstanceOf(ResourceNotFoundException.class);

        verify(onboardingRepository).findById(nonExistentId);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when flagging application with wrong status")
    void shouldThrowExceptionWhenFlaggingWrongStatus() {
        // Given - Create a DRAFT application
        OnboardingApplication draftApplication = new OnboardingApplication(
                testApplicationId,
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

        String reason = "Suspicious";
        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(draftApplication));

        // When / Then
        assertThatThrownBy(() -> complianceService.flagSuspicious(testApplicationId, reason).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Can only flag applications under review");

        verify(onboardingRepository, never()).save(any());
    }

    // ==================== assignToSelf() Tests ====================

    @Test
    @DisplayName("Should successfully assign submitted application to officer")
    void shouldAssignSubmittedApplicationToOfficer() throws Exception {
        // Given - Create a submitted but unassigned application
        OnboardingApplication submittedApp = new OnboardingApplication(
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

        submittedApp.addConsent(new ConsentRecord(
                UUID.randomUUID(), ConsentType.DATA_PROCESSING, true, "127.0.0.1", "I consent", "1.0"));
        submittedApp.addConsent(new ConsentRecord(
                UUID.randomUUID(), ConsentType.TERMS_AND_CONDITIONS, true, "127.0.0.1", "I accept", "1.0"));
        submittedApp.markEmailAsVerified();
        submittedApp.addDocument(new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT, "/path", "image/jpeg", 1024L));
        submittedApp.addDocument(new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PHOTO, "/path", "image/jpeg", 1024L));
        submittedApp.submit();

        String officerId = "officer@abc.nl";

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(submittedApp));
        when(onboardingRepository.save(any(OnboardingApplication.class))).thenReturn(submittedApp);
        doNothing().when(eventPublisher).publish(any());
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<String> result = complianceService.assignToSelf(testApplicationId, officerId);
        String message = result.get();

        // Then
        assertThat(message).contains("assigned successfully");
        assertThat(submittedApp.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(submittedApp.getAssignedTo()).isEqualTo(officerId);

        verify(onboardingRepository).findById(testApplicationId);
        verify(onboardingRepository).save(submittedApp);
        verify(eventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("Should throw exception when assigning with empty officer ID")
    void shouldThrowExceptionWhenAssigningWithEmptyOfficerId() {
        // Given
        String emptyOfficerId = "";

        // When / Then
        assertThatThrownBy(() -> complianceService.assignToSelf(testApplicationId, emptyOfficerId).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Officer ID is required");

        verify(onboardingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw exception when assigning non-existent application")
    void shouldThrowExceptionWhenAssigningNonExistentApplication() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        String officerId = "officer@abc.nl";

        when(onboardingRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> complianceService.assignToSelf(nonExistentId, officerId).join())
                .hasCauseInstanceOf(ResourceNotFoundException.class);

        verify(onboardingRepository).findById(nonExistentId);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when assigning already assigned application")
    void shouldThrowExceptionWhenAssigningAlreadyAssignedApplication() {
        // Given - testApplication is already assigned in setUp
        String officerId = "officer2@abc.nl";

        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(testApplication));

        // When / Then
        assertThatThrownBy(() -> complianceService.assignToSelf(testApplicationId, officerId).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already assigned");

        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when assigning application with wrong status")
    void shouldThrowExceptionWhenAssigningWrongStatus() {
        // Given - Create a DRAFT application
        OnboardingApplication draftApplication = new OnboardingApplication(
                testApplicationId,
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

        String officerId = "officer@abc.nl";
        when(onboardingRepository.findById(testApplicationId)).thenReturn(Optional.of(draftApplication));

        // When / Then
        assertThatThrownBy(() -> complianceService.assignToSelf(testApplicationId, officerId).join())
                .hasCauseInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Can only assign applications that are submitted or under review");

        verify(onboardingRepository, never()).save(any());
    }

    // ==================== listApplicationsByStatus() Tests ====================

    @Test
    @DisplayName("Should return list structure for applications by status (stub implementation)")
    void shouldReturnListStructureForApplicationsByStatus() throws Exception {
        // Given
        ApplicationStatus status = ApplicationStatus.UNDER_REVIEW;
        when(auditRepository.save(any(AuditEvent.class))).thenReturn(null);

        // When
        CompletableFuture<List<OnboardingApplication>> result =
                complianceService.listApplicationsByStatus(status);
        List<OnboardingApplication> applications = result.get();

        // Then
        assertThat(applications).isNotNull();
        assertThat(applications).isEmpty(); // Stub implementation returns empty list
        // Note: In production, this would query the repository by status
    }
}
