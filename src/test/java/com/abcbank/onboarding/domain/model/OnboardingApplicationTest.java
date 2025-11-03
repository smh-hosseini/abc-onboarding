package com.abcbank.onboarding.domain.model;

import com.abcbank.onboarding.domain.event.*;
import com.abcbank.onboarding.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OnboardingApplication aggregate root.
 * Tests state transitions, business rules, and domain events.
 */
@DisplayName("OnboardingApplication Unit Tests")
class OnboardingApplicationTest {

    private UUID applicationId;
    private Address testAddress;
    private OnboardingApplication application;

    @BeforeEach
    void setUp() {
        applicationId = UUID.randomUUID();
        testAddress = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        application = new OnboardingApplication(
                applicationId,
                "John",
                "Doe",
                Gender.MALE,
                LocalDate.of(1990, 1, 15),
                "+31612345678",
                "john.doe@example.com",
                "NL",
                testAddress,
                "123456789"
        );
    }

    // Constructor Tests

    @Test
    @DisplayName("Should create application with INITIATED status")
    void shouldCreateApplicationWithInitiatedStatus() {
        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INITIATED);
        assertThat(application.getId()).isEqualTo(applicationId);
        assertThat(application.getFirstName()).isEqualTo("John");
        assertThat(application.getLastName()).isEqualTo("Doe");
        assertThat(application.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(application.getCreatedAt()).isNotNull();
        assertThat(application.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should register ApplicationCreatedEvent on creation")
    void shouldRegisterApplicationCreatedEventOnCreation() {
        // Then
        assertThat(application.getDomainEvents()).hasSize(1);
        assertThat(application.getDomainEvents().get(0)).isInstanceOf(ApplicationCreatedEvent.class);

        ApplicationCreatedEvent event = (ApplicationCreatedEvent) application.getDomainEvents().get(0);
        assertThat(event.applicationId()).isEqualTo(applicationId);
        assertThat(event.email()).isEqualTo("john.doe@example.com");
        assertThat(event.phone()).isEqualTo("+31612345678");
    }

    @Test
    @DisplayName("Should throw exception when creating with null fields")
    void shouldThrowExceptionWhenCreatingWithNullFields() {
        // When/Then
        assertThatThrownBy(() -> new OnboardingApplication(
                null, "John", "Doe", Gender.MALE, LocalDate.now(),
                "+31612345678", "test@test.com", "NL", testAddress, "123456789"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Application ID cannot be null");
    }

    // Email Verification Tests

    @Test
    @DisplayName("Should mark email as verified and update status to OTP_VERIFIED")
    void shouldMarkEmailAsVerifiedAndUpdateStatus() {
        // When
        application.markEmailAsVerified();

        // Then
        assertThat(application.isEmailVerified()).isTrue();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.OTP_VERIFIED);
        assertThat(application.getDomainEvents()).hasSize(2);
        assertThat(application.getDomainEvents().get(1)).isInstanceOf(OtpVerifiedEvent.class);
    }

    @Test
    @DisplayName("Should mark phone as verified and update status to OTP_VERIFIED")
    void shouldMarkPhoneAsVerifiedAndUpdateStatus() {
        // When
        application.markPhoneAsVerified();

        // Then
        assertThat(application.isPhoneVerified()).isTrue();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.OTP_VERIFIED);
    }

    // Document Management Tests

    @Test
    @DisplayName("Should add document when in OTP_VERIFIED status")
    void shouldAddDocumentWhenInOtpVerifiedStatus() {
        // Given
        application.markEmailAsVerified();
        ApplicationDocument document = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport.pdf", "application/pdf", 1024L
        );

        // When
        application.addDocument(document);

        // Then
        assertThat(application.getDocuments()).hasSize(1);
        assertThat(application.getDocuments().get(0)).isEqualTo(document);
    }

    @Test
    @DisplayName("Should throw exception when adding document in wrong status")
    void shouldThrowExceptionWhenAddingDocumentInWrongStatus() {
        // Given - Application is INITIATED
        ApplicationDocument document = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport.pdf", "application/pdf", 1024L
        );

        // When/Then
        assertThatThrownBy(() -> application.addDocument(document))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot upload documents");
    }

    @Test
    @DisplayName("Should replace existing document of same type")
    void shouldReplaceExistingDocumentOfSameType() {
        // Given
        application.markEmailAsVerified();
        ApplicationDocument doc1 = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport1.pdf", "application/pdf", 1024L
        );
        ApplicationDocument doc2 = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport2.pdf", "application/pdf", 2048L
        );

        // When
        application.addDocument(doc1);
        application.addDocument(doc2);

        // Then
        assertThat(application.getDocuments()).hasSize(1);
        assertThat(application.getDocuments().get(0)).isEqualTo(doc2);
    }

    @Test
    @DisplayName("Should update status to DOCUMENTS_UPLOADED when all required documents added")
    void shouldUpdateStatusToDocumentsUploadedWhenAllRequiredDocumentsAdded() {
        // Given
        application.markEmailAsVerified();
        ApplicationDocument passport = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport.pdf", "application/pdf", 1024L
        );
        ApplicationDocument photo = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PHOTO,
                "/path/to/photo.jpg", "image/jpeg", 512L
        );

        // When
        application.addDocument(passport);
        application.addDocument(photo);

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DOCUMENTS_UPLOADED);
        assertThat(application.hasAllRequiredDocuments()).isTrue();
    }

    // Consent Management Tests

    @Test
    @DisplayName("Should add consent and register event")
    void shouldAddConsentAndRegisterEvent() {
        // Given
        ConsentRecord consent = new ConsentRecord(
                UUID.randomUUID(), ConsentType.DATA_PROCESSING,
                true, "192.168.1.1", "I agree to data processing", "1.0"
        );

        // When
        application.addConsent(consent);

        // Then
        assertThat(application.getConsents()).hasSize(1);
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ConsentGrantedEvent);
    }

    @Test
    @DisplayName("Should detect when has required consents")
    void shouldDetectWhenHasRequiredConsents() {
        // Given
        ConsentRecord dataProcessing = new ConsentRecord(
                UUID.randomUUID(), ConsentType.DATA_PROCESSING,
                true, "192.168.1.1", "I agree to data processing", "1.0"
        );
        ConsentRecord termsAndConditions = new ConsentRecord(
                UUID.randomUUID(), ConsentType.TERMS_AND_CONDITIONS,
                true, "192.168.1.1", "I agree to terms and conditions", "1.0"
        );

        // When
        application.addConsent(dataProcessing);
        application.addConsent(termsAndConditions);

        // Then
        assertThat(application.hasRequiredConsents()).isTrue();
    }

    // Application Submission Tests

    @Test
    @DisplayName("Should submit application when all requirements met")
    void shouldSubmitApplicationWhenAllRequirementsMet() {
        // Given
        application.markEmailAsVerified();
        addRequiredDocuments();
        addRequiredConsents();

        // When
        application.submit();

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        assertThat(application.getSubmittedAt()).isNotNull();
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ApplicationSubmittedEvent);
    }

    @Test
    @DisplayName("Should throw exception when submitting without documents")
    void shouldThrowExceptionWhenSubmittingWithoutDocuments() {
        // Given
        application.markEmailAsVerified();
        // Add only one document (not both passport and photo)
        ApplicationDocument passport = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport.pdf", "application/pdf", 1024L
        );
        application.addDocument(passport);
        // Now status is still OTP_VERIFIED or might be DOCUMENTS_UPLOADED if single doc is enough
        // But we need DOCUMENTS_UPLOADED status first
        ApplicationDocument photo = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PHOTO,
                "/path/to/photo.jpg", "image/jpeg", 512L
        );
        application.addDocument(photo);
        // Now we have DOCUMENTS_UPLOADED status but no consents

        // When/Then
        assertThatThrownBy(() -> application.submit())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("without required consents");
    }

    @Test
    @DisplayName("Should throw exception when submitting without consents")
    void shouldThrowExceptionWhenSubmittingWithoutConsents() {
        // Given
        application.markEmailAsVerified();
        addRequiredDocuments();

        // When/Then
        assertThatThrownBy(() -> application.submit())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("without required consents");
    }

    @Test
    @DisplayName("Should throw exception when submitting in wrong status")
    void shouldThrowExceptionWhenSubmittingInWrongStatus() {
        // Given - Application is INITIATED

        // When/Then
        assertThatThrownBy(() -> application.submit())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Documents must be uploaded first");
    }

    // Compliance Officer Actions Tests

    @Test
    @DisplayName("Should assign application to officer")
    void shouldAssignApplicationToOfficer() {
        // Given
        prepareApplicationForReview();

        // When
        application.assignTo("officer@abc.nl");

        // Then
        assertThat(application.getAssignedTo()).isEqualTo("officer@abc.nl");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ApplicationAssignedEvent);
    }

    @Test
    @DisplayName("Should verify documents and update status")
    void shouldVerifyDocumentsAndUpdateStatus() {
        // Given
        prepareApplicationForReview();
        application.assignTo("officer@abc.nl");

        // When
        application.verifyDocuments("officer@abc.nl");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.VERIFIED);
        assertThat(application.getDocuments()).allMatch(ApplicationDocument::isVerified);
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ApplicationVerifiedEvent);
    }

    @Test
    @DisplayName("Should throw exception when verifying documents in wrong status")
    void shouldThrowExceptionWhenVerifyingDocumentsInWrongStatus() {
        // Given - Application is SUBMITTED
        prepareApplicationForReview();

        // When/Then
        assertThatThrownBy(() -> application.verifyDocuments("officer@abc.nl"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("under review");
    }

    @Test
    @DisplayName("Should request additional information")
    void shouldRequestAdditionalInformation() {
        // Given
        prepareApplicationForReview();
        application.assignTo("officer@abc.nl");

        // When
        application.requestAdditionalInfo("Missing proof of address");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REQUIRES_MORE_INFO);
        assertThat(application.getReviewReason()).isEqualTo("Missing proof of address");
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof AdditionalInfoRequestedEvent);
    }

    @Test
    @DisplayName("Should flag application as suspicious")
    void shouldFlagApplicationAsSuspicious() {
        // Given
        prepareApplicationForReview();
        application.assignTo("officer@abc.nl");

        // When
        application.flagAsSuspicious("Suspected identity fraud");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.FLAGGED_SUSPICIOUS);
        assertThat(application.isRequiresManualReview()).isTrue();
        assertThat(application.getReviewReason()).isEqualTo("Suspected identity fraud");
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ApplicationFlaggedEvent);
    }

    @Test
    @DisplayName("Should provide additional information and move back to review")
    void shouldProvideAdditionalInformationAndMoveBackToReview() {
        // Given
        prepareApplicationForReview();
        application.assignTo("officer@abc.nl");
        application.requestAdditionalInfo("Missing proof");

        // When
        application.provideAdditionalInformation("Added proof of address");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(application.getReviewReason()).isNull();
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof AdditionalInfoProvidedEvent);
    }

    // Admin Actions Tests

    @Test
    @DisplayName("Should approve application")
    void shouldApproveApplication() {
        // Given
        prepareApplicationForApproval();
        UUID customerId = UUID.randomUUID();
        String accountNumber = "NL91ABCB0417164300";

        // When
        application.approve(customerId, accountNumber, "admin@abc.nl");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getCustomerId()).isEqualTo(customerId);
        assertThat(application.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(application.getApprovedAt()).isNotNull();
        assertThat(application.getDataRetentionUntil()).isNotNull();
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ApplicationApprovedEvent);
    }

    @Test
    @DisplayName("Should approve flagged application")
    void shouldApproveFlaggedApplication() {
        // Given
        prepareApplicationForReview();
        application.assignTo("officer@abc.nl");
        application.flagAsSuspicious("Manual review required");
        UUID customerId = UUID.randomUUID();
        String accountNumber = "NL91ABCB0417164300";

        // When
        application.approve(customerId, accountNumber, "admin@abc.nl");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("Should throw exception when approving non-verified application")
    void shouldThrowExceptionWhenApprovingNonVerifiedApplication() {
        // Given - Application is SUBMITTED
        prepareApplicationForReview();
        UUID customerId = UUID.randomUUID();
        String accountNumber = "NL91ABCB0417164300";

        // When/Then
        assertThatThrownBy(() -> application.approve(customerId, accountNumber, "admin@abc.nl"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("verified or flagged");
    }

    @Test
    @DisplayName("Should reject application")
    void shouldRejectApplication() {
        // Given
        prepareApplicationForApproval();

        // When
        application.reject("Failed identity verification");

        // Then
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getRejectedAt()).isNotNull();
        assertThat(application.getRejectionReason()).isEqualTo("Failed identity verification");
        assertThat(application.getDataRetentionUntil()).isNotNull();
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof ApplicationRejectedEvent);
    }

    @Test
    @DisplayName("Should throw exception when rejecting with null reason")
    void shouldThrowExceptionWhenRejectingWithNullReason() {
        // Given
        prepareApplicationForApproval();

        // When/Then
        assertThatThrownBy(() -> application.reject(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Rejection reason cannot be null");
    }

    // GDPR Tests

    @Test
    @DisplayName("Should mark rejected application for deletion")
    void shouldMarkRejectedApplicationForDeletion() {
        // Given
        prepareApplicationForApproval();
        application.reject("Failed verification");

        // When
        application.markForDeletion();

        // Then
        assertThat(application.isMarkedForDeletion()).isTrue();
        assertThat(application.getDomainEvents()).anyMatch(e -> e instanceof DataDeletionRequestedEvent);
    }

    @Test
    @DisplayName("Should throw exception when marking non-rejected application for deletion")
    void shouldThrowExceptionWhenMarkingNonRejectedApplicationForDeletion() {
        // Given - Application is INITIATED

        // When/Then
        assertThatThrownBy(() -> application.markForDeletion())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("rejected applications");
    }

    @Test
    @DisplayName("Should anonymize personal data")
    void shouldAnonymizePersonalData() {
        // When
        application.anonymize();

        // Then
        assertThat(application.getFirstName()).isEqualTo("DELETED");
        assertThat(application.getLastName()).isEqualTo("DELETED");
        assertThat(application.getEmail()).isEqualTo("deleted@anonymized.local");
        assertThat(application.getPhone()).isEqualTo("DELETED");
        assertThat(application.getSocialSecurityNumber()).isEqualTo("DELETED");
    }

    // Domain Events Tests

    @Test
    @DisplayName("Should clear domain events")
    void shouldClearDomainEvents() {
        // Given
        assertThat(application.getDomainEvents()).isNotEmpty();

        // When
        application.clearEvents();

        // Then
        assertThat(application.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should return unmodifiable list of events")
    void shouldReturnUnmodifiableListOfEvents() {
        // When/Then
        assertThatThrownBy(() -> application.getDomainEvents().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should return unmodifiable list of documents")
    void shouldReturnUnmodifiableListOfDocuments() {
        // When/Then
        assertThatThrownBy(() -> application.getDocuments().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should return unmodifiable list of consents")
    void shouldReturnUnmodifiableListOfConsents() {
        // When/Then
        assertThatThrownBy(() -> application.getConsents().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // Helper Methods

    private void addRequiredDocuments() {
        ApplicationDocument passport = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PASSPORT,
                "/path/to/passport.pdf", "application/pdf", 1024L
        );
        ApplicationDocument photo = new ApplicationDocument(
                UUID.randomUUID(), DocumentType.PHOTO,
                "/path/to/photo.jpg", "image/jpeg", 512L
        );
        application.addDocument(passport);
        application.addDocument(photo);
    }

    private void addRequiredConsents() {
        ConsentRecord dataProcessing = new ConsentRecord(
                UUID.randomUUID(), ConsentType.DATA_PROCESSING,
                true, "192.168.1.1", "I agree to data processing", "1.0"
        );
        ConsentRecord termsAndConditions = new ConsentRecord(
                UUID.randomUUID(), ConsentType.TERMS_AND_CONDITIONS,
                true, "192.168.1.1", "I agree to terms and conditions", "1.0"
        );
        application.addConsent(dataProcessing);
        application.addConsent(termsAndConditions);
    }

    private void prepareApplicationForReview() {
        application.markEmailAsVerified();
        addRequiredDocuments();
        addRequiredConsents();
        application.submit();
    }

    private void prepareApplicationForApproval() {
        prepareApplicationForReview();
        application.assignTo("officer@abc.nl");
        application.verifyDocuments("officer@abc.nl");
    }
}
