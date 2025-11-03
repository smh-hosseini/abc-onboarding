package com.abcbank.onboarding.e2e;

import com.abcbank.onboarding.AbstractIntegrationTest;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import com.abcbank.onboarding.domain.port.out.OtpVerificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End Integration Tests for Complete Onboarding Workflows.
 *
 * Tests the complete user journeys from application creation to final approval/rejection.
 * Each test follows a real-world scenario without repeating common setup code.
 */
@DisplayName("Complete Onboarding Workflow E2E Tests")
class CompleteOnboardingWorkflowE2ETest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OnboardingRepository onboardingRepository;

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== Happy Path: Complete Approval Flow ====================

    @Test
    @DisplayName("E2E: Complete onboarding workflow - from application to approval")
    void shouldCompleteFullOnboardingWorkflowToApproval() throws Exception {
        // Step 1: Create application (anonymous user)
        String createApplicationJson = """
            {
                "firstName": "Emma",
                "lastName": "Johnson",
                "gender": "FEMALE",
                "dateOfBirth": "1992-05-15",
                "phone": "+31612345678",
                "email": "emma.johnson@example.com",
                "nationality": "NL",
                "residentialAddress": {
                    "street": "Damrak",
                    "houseNumber": "100",
                    "postalCode": "1012LM",
                    "city": "Amsterdam",
                    "country": "NL"
                },
                "socialSecurityNumber": "123456789",
                "consents": [
                    {
                        "type": "DATA_PROCESSING",
                        "given": true,
                        "ipAddress": "127.0.0.1",
                        "text": "I consent to data processing",
                        "version": "1.0"
                    },
                    {
                        "type": "TERMS_AND_CONDITIONS",
                        "given": true,
                        "ipAddress": "127.0.0.1",
                        "text": "I accept terms and conditions",
                        "version": "1.0"
                    }
                ]
            }
            """;

        String createResponse = performAsAnonymous(
                post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").exists())
                .andReturn().getResponse().getContentAsString();

        UUID applicationId = UUID.fromString(objectMapper.readTree(createResponse).get("applicationId").asText());

        // Step 2: Manually add consents since they're not part of the API
        addRequiredConsents(applicationId, "I consent to data processing", "I accept terms and conditions");

        // Step 3: Inject OTP for email verification
        // Note: We manually inject the OTP since we can't retrieve the random OTP generated
        injectOtpForApplication(applicationId, "123456");

        // Step 3: Verify email with OTP (anonymous) - use test OTP 123456
        String verifyOtpJson = """
            {
                "otp": "123456",
                "channel": "EMAIL"
            }
            """;

        performAsAnonymous(
                post("/api/v1/onboarding/applications/{id}/verify-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyOtpJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // Step 4: Upload documents as applicant
        uploadDocument(applicationId, "passport.jpg", "PASSPORT");
        uploadDocument(applicationId, "photo.jpg", "PHOTO");

        // Wait for async document uploads to complete
        Thread.sleep(100);

        // Step 5: Submit application as applicant
        performAsyncAsApplicant(applicationId,
                post("/api/v1/applicant/applications/{id}/submit", applicationId))
                .andExpect(status().isAccepted());

        // Wait for async operation to complete
        Thread.sleep(100);

        // Verify status is SUBMITTED
        OnboardingApplication app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);

        // Step 6: Compliance officer assigns application to themselves
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/assign-to-me", applicationId))
                .andExpect(status().isAccepted());

        // Wait for async operation to complete
        Thread.sleep(100);

        // Verify status is UNDER_REVIEW
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);

        // Step 7: Compliance officer verifies documents
        String verifyJson = """
            {
                "notes": "All documents verified successfully"
            }
            """;

        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/verify", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson))
                .andExpect(status().isAccepted());

        // Wait for async operation to complete
        Thread.sleep(100);

        // Verify status is VERIFIED
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.VERIFIED);

        // Step 8: Admin approves application
        String approveJson = """
            {
                "approvalNotes": "Excellent application, approved"
            }
            """;

        performAsyncAsAdmin(
                post("/api/v1/admin/applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson))
                .andExpect(status().isAccepted());

        // Wait for async operation to complete
        Thread.sleep(100);

        // Verify final status is APPROVED
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(app.getAccountNumber()).isNotNull();
        assertThat(app.getAccountNumber()).startsWith("NL");
        assertThat(app.getAccountNumber()).hasSize(18);

        // Step 9: Applicant checks application status
        performAsApplicant(applicationId,
                get("/api/v1/applicant/applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.accountNumber").value(app.getAccountNumber()));
    }

    // ==================== Rejection Flow ====================

    @Test
    @DisplayName("E2E: Complete onboarding workflow - application rejection")
    void shouldCompleteFullRejectionWorkflow() throws Exception {
        // Step 1-6: Same as approval flow up to UNDER_REVIEW
        UUID applicationId = createAndSubmitApplication("John", "Smith", "john.smith@example.com");

        // Assign to compliance officer
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/assign-to-me", applicationId))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify documents
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/verify", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Verified\"}"))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Step 7: Admin rejects application
        String rejectJson = """
            {
                "rejectionReason": "Documents do not meet requirements"
            }
            """;

        performAsyncAsAdmin(
                post("/api/v1/admin/applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectJson))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify status is REJECTED
        var app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(app.getRejectionReason()).isEqualTo("Documents do not meet requirements");

        // Step 8: Applicant requests data deletion (GDPR)
        performAsyncAsApplicant(applicationId,
                delete("/api/v1/applicant/applications/{id}/delete-request", applicationId))
                .andExpect(status().isAccepted());

        // Verify marked for deletion
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.isMarkedForDeletion()).isTrue();
    }

    // ==================== Additional Info Flow ====================

    @Test
    @DisplayName("E2E: Workflow with additional info request")
    void shouldHandleAdditionalInfoRequestWorkflow() throws Exception {
        // Step 1-5: Create and submit application
        UUID applicationId = createAndSubmitApplication("Sarah", "Williams", "sarah.williams@example.com");

        // Step 2: Assign to compliance officer
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/assign-to-me", applicationId))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Step 3: Compliance officer requests additional information
        String requestInfoJson = """
            {
                "reason": "Please provide proof of address"
            }
            """;

        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/request-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestInfoJson))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify status is REQUIRES_MORE_INFO
        var app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REQUIRES_MORE_INFO);

        // Step 4: Applicant provides additional information
        String provideInfoJson = """
            {
                "information": "I have uploaded proof of address document"
            }
            """;

        performAsyncAsApplicant(applicationId,
                post("/api/v1/applicant/applications/{id}/provide-info", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provideInfoJson))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify status returns to UNDER_REVIEW
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);

        // Step 5: Continue with verification and approval
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/verify", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Additional info provided and verified\"}"))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        performAsyncAsAdmin(
                post("/api/v1/admin/applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNotes\": \"Approved after additional info\"}"))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify final status
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    // ==================== Flagged Suspicious Flow ====================

    @Test
    @DisplayName("E2E: Workflow with flagged suspicious application")
    void shouldHandleFlaggedSuspiciousWorkflow() throws Exception {
        // Step 1-5: Create and submit application
        UUID applicationId = createAndSubmitApplication("Michael", "Brown", "michael.brown@example.com");

        // Step 2: Assign to compliance officer
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/assign-to-me", applicationId))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Step 3: Compliance officer flags as suspicious
        String flagJson = """
            {
                "reason": "Suspicious transaction history detected"
            }
            """;

        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/flag", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flagJson))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify status is FLAGGED_SUSPICIOUS
        var app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.FLAGGED_SUSPICIOUS);

        // Step 4: Admin reviews and decides to approve anyway
        String approveJson = """
            {
                "approvalNotes": "Reviewed suspicious flag - false positive, approved"
            }
            """;

        performAsyncAsAdmin(
                post("/api/v1/admin/applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveJson))
                .andExpect(status().isAccepted());
        Thread.sleep(100);

        // Verify final status
        app = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    // ==================== Access Control Validation ====================

    @Test
    @DisplayName("E2E: Validate role-based access control throughout workflow")
    void shouldEnforceRoleBasedAccessControl() throws Exception {
        // Create application
        UUID applicationId = createAndSubmitApplication("Alice", "Anderson", "alice.anderson@example.com");

        // Test 1: Applicant cannot assign application
        performAsyncAsApplicant(applicationId,
                post("/api/v1/compliance/applications/{id}/assign-to-me", applicationId))
                .andExpect(status().isForbidden());

        // Test 2: Compliance officer cannot approve
        performAsyncAsComplianceOfficer(
                post("/api/v1/admin/applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNotes\": \"test\"}"))
                .andExpect(status().isForbidden());

        // Test 3: Wrong applicant cannot access another applicant's application
        UUID wrongApplicationId = UUID.randomUUID();
        performAsApplicant(wrongApplicationId,
                get("/api/v1/applicant/applications/{id}", applicationId))
                .andExpect(status().isForbidden());

        // Test 4: Anonymous user cannot approve
        performAsAnonymous(
                post("/api/v1/admin/applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNotes\": \"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Error Handling Validation ====================

    @Test
    @DisplayName("E2E: Validate error handling for invalid transitions")
    void shouldHandleInvalidStatusTransitions() throws Exception {
        // Create application but don't submit
        UUID applicationId = createBasicApplication("Bob", "Wilson", "bob.wilson@example.com");

        // Test 1: Cannot assign unsubmitted application
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/assign-to-me", applicationId))
                .andExpect(status().is5xxServerError());

        // Submit application
        performAsyncAsApplicant(applicationId,
                post("/api/v1/applicant/applications/{id}/submit", applicationId))
                .andExpect(status().isAccepted());

        // Test 2: Cannot verify without assignment
        performAsyncAsComplianceOfficer(
                post("/api/v1/compliance/applications/{id}/verify", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"test\"}"))
                .andExpect(status().is5xxServerError());

        // Test 3: Cannot approve without verification
        performAsyncAsAdmin(
                post("/api/v1/admin/applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalNotes\": \"test\"}"))
                .andExpect(status().is5xxServerError());
    }

    // ==================== Helper Methods ====================

    /**
     * Adds required consents to an application.
     * Consents are not part of the API and must be manually added.
     */
    private void addRequiredConsents(UUID applicationId, String consentText, String acceptText) {
        OnboardingApplication app = onboardingRepository.findById(applicationId).orElseThrow();
        app.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.DATA_PROCESSING,
                true,
                "127.0.0.1",
                consentText,
                "1.0"
        ));
        app.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.TERMS_AND_CONDITIONS,
                true,
                "127.0.0.1",
                acceptText,
                "1.0"
        ));
        onboardingRepository.save(app);
    }

    /**
     * Injects an OTP verification record for testing.
     * Required because we cannot retrieve the random OTP generated by sendOtp.
     */
    private void injectOtpForApplication(UUID applicationId, String otp) {
        String otpHash = passwordEncoder.encode(otp);
        OtpVerification otpVerification = new OtpVerification(
                UUID.randomUUID(),
                applicationId,
                OtpVerification.OtpChannel.EMAIL,
                otpHash,
                LocalDateTime.now().plusMinutes(10)
        );
        otpVerificationRepository.save(otpVerification);
    }

    /**
     * Uploads a document for an application.
     * Document upload is async and returns 202 Accepted.
     */
    private void uploadDocument(UUID applicationId, String fileName, String documentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "image/jpeg",
                "fake document content".getBytes()
        );

        performAsyncAsApplicant(applicationId,
                multipart("/api/v1/applicant/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", documentType))
                .andExpect(status().isAccepted());
    }

    /**
     * Performs an async operation and waits for it to complete.
     */
    private void performAsyncAndWait(Runnable operation) throws Exception {
        operation.run();
        Thread.sleep(100);
    }

    private UUID createBasicApplication(String firstName, String lastName, String email) throws Exception {
        String json = String.format("""
            {
                "firstName": "%s",
                "lastName": "%s",
                "gender": "MALE",
                "dateOfBirth": "1990-01-01",
                "phone": "+31612345678",
                "email": "%s",
                "nationality": "NL",
                "residentialAddress": {
                    "street": "Street",
                    "houseNumber": "1",
                    "postalCode": "1234AB",
                    "city": "Amsterdam",
                    "country": "NL"
                },
                "socialSecurityNumber": "123456789",
                "consents": [
                    {"type": "DATA_PROCESSING", "given": true, "ipAddress": "127.0.0.1", "text": "I consent", "version": "1.0"},
                    {"type": "TERMS_AND_CONDITIONS", "given": true, "ipAddress": "127.0.0.1", "text": "I accept", "version": "1.0"}
                ]
            }
            """, firstName, lastName, email);

        String response = performAsAnonymous(
                post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID applicationId = UUID.fromString(objectMapper.readTree(response).get("applicationId").asText());

        // Manually add consents since they're not part of the API
        addRequiredConsents(applicationId, "I consent", "I accept");

        // Verify email - Manually inject OTP
        injectOtpForApplication(applicationId, "123456");

        String verifyOtpJson = """
            {
                "otp": "123456",
                "channel": "EMAIL"
            }
            """;

        performAsAnonymous(
                post("/api/v1/onboarding/applications/{id}/verify-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyOtpJson))
                .andExpect(status().isOk());

        // Upload documents
        uploadDocument(applicationId, "passport.jpg", "PASSPORT");
        uploadDocument(applicationId, "photo.jpg", "PHOTO");

        // Wait for async document uploads to complete
        Thread.sleep(100);

        return applicationId;
    }

    private UUID createAndSubmitApplication(String firstName, String lastName, String email) throws Exception {
        UUID applicationId = createBasicApplication(firstName, lastName, email);

        // Submit
        performAsyncAsApplicant(applicationId,
                post("/api/v1/applicant/applications/{id}/submit", applicationId))
                .andExpect(status().isAccepted());

        // Wait for async operation to complete
        Thread.sleep(100);

        return applicationId;
    }
}
