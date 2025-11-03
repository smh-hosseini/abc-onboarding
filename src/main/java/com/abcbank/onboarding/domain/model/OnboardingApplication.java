package com.abcbank.onboarding.domain.model;

import com.abcbank.onboarding.domain.event.*;
import com.abcbank.onboarding.domain.exception.InvalidStatusTransitionException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Aggregate root representing a customer onboarding application.
 * Enforces business rules and manages state transitions.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OnboardingApplication {
    @EqualsAndHashCode.Include
    private UUID id;
    private ApplicationStatus status;
    private Long version;

    // Personal Information (PII - will be encrypted at persistence layer)
    private String firstName;
    private String lastName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String nationality;
    private Address residentialAddress;
    private String socialSecurityNumber;

    // Verification Status
    private boolean emailVerified;
    private boolean phoneVerified;

    // Documents
    private final List<ApplicationDocument> documents = new ArrayList<>();

    // Consents
    private final List<ConsentRecord> consents = new ArrayList<>();

    // Customer Reference (after approval)
    private UUID customerId;
    private String accountNumber;

    // Review Workflow
    private boolean requiresManualReview;
    private String reviewReason;
    private String assignedTo;

    // GDPR
    private boolean markedForDeletion;
    private LocalDateTime dataRetentionUntil;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private String rejectionReason;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Constructor for new application
    public OnboardingApplication(
            UUID id,
            String firstName,
            String lastName,
            Gender gender,
            LocalDate dateOfBirth,
            String phone,
            String email,
            String nationality,
            Address residentialAddress,
            String socialSecurityNumber
    ) {
        this.id = Objects.requireNonNull(id, "Application ID cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name cannot be null");
        this.gender = Objects.requireNonNull(gender, "Gender cannot be null");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "Date of birth cannot be null");
        this.phone = Objects.requireNonNull(phone, "Phone cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.nationality = Objects.requireNonNull(nationality, "Nationality cannot be null");
        this.residentialAddress = Objects.requireNonNull(residentialAddress, "Address cannot be null");
        this.socialSecurityNumber = Objects.requireNonNull(socialSecurityNumber, "SSN cannot be null");

        this.status = ApplicationStatus.INITIATED;
        this.createdAt = LocalDateTime.now();
        this.version = 0L;

        registerEvent(new ApplicationCreatedEvent(id, email, phone));
    }

    // Verification Management
    public void markEmailAsVerified() {
        this.emailVerified = true;
        checkAndUpdateOtpVerifiedStatus();
    }

    public void markPhoneAsVerified() {
        this.phoneVerified = true;
        checkAndUpdateOtpVerifiedStatus();
    }

    private void checkAndUpdateOtpVerifiedStatus() {
        // If either email or phone is verified, mark as OTP_VERIFIED
        if ((emailVerified || phoneVerified) && status == ApplicationStatus.INITIATED) {
            this.status = ApplicationStatus.OTP_VERIFIED;
            registerEvent(new OtpVerifiedEvent(id));
        }
    }

    // Document Management
    public void addDocument(ApplicationDocument document) {
        if (status != ApplicationStatus.OTP_VERIFIED && status != ApplicationStatus.DOCUMENTS_UPLOADED) {
            throw new InvalidStatusTransitionException(
                    "Cannot upload documents for application in status: " + status
            );
        }

        // Check if document type already exists
        boolean alreadyExists = documents.stream()
                .anyMatch(doc -> doc.getDocumentType() == document.getDocumentType());

        if (alreadyExists) {
            // Replace existing document
            documents.removeIf(doc -> doc.getDocumentType() == document.getDocumentType());
        }

        documents.add(document);

        // Update status if we have all required documents
        if (hasAllRequiredDocuments()) {
            this.status = ApplicationStatus.DOCUMENTS_UPLOADED;
        }

        registerEvent(new DocumentUploadedEvent(id, document.getId(), document.getDocumentType()));
    }

    public boolean hasAllRequiredDocuments() {
        return hasDocument(DocumentType.PASSPORT) && hasDocument(DocumentType.PHOTO);
    }

    private boolean hasDocument(DocumentType type) {
        return documents.stream()
                .anyMatch(doc -> doc.getDocumentType() == type);
    }

    // Consent Management
    public void addConsent(ConsentRecord consent) {
        consents.add(consent);
        registerEvent(new ConsentGrantedEvent(id, consent.getConsentType()));
    }

    public boolean hasRequiredConsents() {
        return hasActiveConsent(ConsentType.DATA_PROCESSING) &&
                hasActiveConsent(ConsentType.TERMS_AND_CONDITIONS);
    }

    private boolean hasActiveConsent(ConsentType type) {
        return consents.stream()
                .anyMatch(c -> c.getConsentType() == type && c.isActive());
    }

    // Application Submission
    public void submit() {
        if (status != ApplicationStatus.DOCUMENTS_UPLOADED) {
            throw new InvalidStatusTransitionException(
                    "Cannot submit application in status: " + status +
                            ". Documents must be uploaded first."
            );
        }

        if (!hasAllRequiredDocuments()) {
            throw new InvalidStatusTransitionException(
                    "Cannot submit application without all required documents"
            );
        }

        if (!hasRequiredConsents()) {
            throw new InvalidStatusTransitionException(
                    "Cannot submit application without required consents"
            );
        }

        this.status = ApplicationStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();

        registerEvent(new ApplicationSubmittedEvent(id, email));
    }

    // Compliance Officer Actions
    public void assignTo(String officerEmail) {
        if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.UNDER_REVIEW) {
            throw new InvalidStatusTransitionException(
                    "Can only assign applications that are submitted or under review"
            );
        }

        this.assignedTo = officerEmail;
        if (status == ApplicationStatus.SUBMITTED) {
            this.status = ApplicationStatus.UNDER_REVIEW;
        }

        registerEvent(new ApplicationAssignedEvent(id, officerEmail));
    }

    public void verifyDocuments(String verifiedBy) {
        if (status != ApplicationStatus.UNDER_REVIEW) {
            throw new InvalidStatusTransitionException(
                    "Can only verify applications that are under review"
            );
        }

        // Mark all documents as verified
        documents.forEach(doc -> doc.verify(verifiedBy));

        this.status = ApplicationStatus.VERIFIED;

        registerEvent(new ApplicationVerifiedEvent(id, verifiedBy));
    }

    public void requestAdditionalInfo(String reason) {
        if (status != ApplicationStatus.UNDER_REVIEW) {
            throw new InvalidStatusTransitionException(
                    "Can only request additional info for applications under review"
            );
        }

        this.status = ApplicationStatus.REQUIRES_MORE_INFO;
        this.reviewReason = reason;

        registerEvent(new AdditionalInfoRequestedEvent(id, reason));
    }

    public void flagAsSuspicious(String reason) {
        if (status != ApplicationStatus.UNDER_REVIEW) {
            throw new InvalidStatusTransitionException(
                    "Can only flag applications that are under review"
            );
        }

        this.status = ApplicationStatus.FLAGGED_SUSPICIOUS;
        this.reviewReason = reason;
        this.requiresManualReview = true;

        registerEvent(new ApplicationFlaggedEvent(id, reason));
    }

    public void provideAdditionalInformation(String information) {
        if (status != ApplicationStatus.REQUIRES_MORE_INFO) {
            throw new InvalidStatusTransitionException(
                    "Can only provide additional info when status is REQUIRES_MORE_INFO. Current status: " + status
            );
        }

        this.status = ApplicationStatus.UNDER_REVIEW;
        this.reviewReason = null; // Clear the reason since info was provided

        registerEvent(new AdditionalInfoProvidedEvent(id, information));
    }

    // Admin Actions
    public void approve(UUID customerId, String accountNumber, String approvedBy) {
        if (status != ApplicationStatus.VERIFIED && status != ApplicationStatus.FLAGGED_SUSPICIOUS) {
            throw new InvalidStatusTransitionException(
                    "Can only approve verified or flagged applications"
            );
        }

        this.status = ApplicationStatus.APPROVED;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.approvedAt = LocalDateTime.now();
        this.dataRetentionUntil = LocalDateTime.now().plusYears(5);

        registerEvent(new ApplicationApprovedEvent(id, customerId, accountNumber, approvedBy, email));
    }

    public void reject(String reason) {
        if (status != ApplicationStatus.VERIFIED &&
                status != ApplicationStatus.FLAGGED_SUSPICIOUS &&
                status != ApplicationStatus.UNDER_REVIEW) {
            throw new InvalidStatusTransitionException(
                    "Can only reject applications that are under review, verified, or flagged"
            );
        }

        this.status = ApplicationStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
        this.rejectionReason = Objects.requireNonNull(reason, "Rejection reason cannot be null");
        this.dataRetentionUntil = LocalDateTime.now().plusDays(90);

        registerEvent(new ApplicationRejectedEvent(id, reason, email));
    }

    // GDPR
    public void markForDeletion() {
        if (status != ApplicationStatus.REJECTED) {
            throw new InvalidStatusTransitionException(
                    "Can only mark rejected applications for deletion"
            );
        }
        this.markedForDeletion = true;
        registerEvent(new DataDeletionRequestedEvent(id));
    }

    public void anonymize() {
        this.firstName = "DELETED";
        this.lastName = "DELETED";
        this.email = "deleted@anonymized.local";
        this.phone = "DELETED";
        this.socialSecurityNumber = "DELETED";
        this.residentialAddress = new Address("DELETED", "0", "0000XX", "DELETED", "XX");
    }

    // Domain Events
    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearEvents() {
        domainEvents.clear();
    }

    // Custom getters for collections (return unmodifiable views)
    public List<ApplicationDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    public List<ConsentRecord> getConsents() {
        return Collections.unmodifiableList(consents);
    }
}
