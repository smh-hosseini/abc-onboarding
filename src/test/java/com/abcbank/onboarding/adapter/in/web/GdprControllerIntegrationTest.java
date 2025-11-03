package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.AbstractIntegrationTest;
import com.abcbank.onboarding.domain.model.*;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Refactored GDPR Controller Integration Tests using utility methods.
 * Demonstrates clean authentication handling with Spring Security test support.
 */
@DisplayName("GDPR Controller Integration Tests")
class GdprControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OnboardingRepository onboardingRepository;

    @Test
    @DisplayName("Should request data deletion for rejected application with applicant role")
    void shouldRequestDataDeletionForRejectedApplication() throws Exception {
        // Given - Create and reject application
        UUID applicationId = createRejectedApplication();

        // When - Request data deletion AS APPLICANT (much cleaner!)
        performAsyncAsApplicant(applicationId,
                delete("/api/v1/applicant/applications/{id}/delete-request", applicationId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());

        // Then - Verify marked for deletion
        OnboardingApplication application = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(application.isMarkedForDeletion()).isTrue();
    }

    @Test
    @DisplayName("Should reject data deletion request without authentication")
    void shouldRejectDataDeletionWithoutAuth() throws Exception {
        // Given
        UUID applicationId = createRejectedApplication();

        // When/Then - No authentication (anonymous)
        performAsAnonymous(delete("/api/v1/applicant/applications/{id}/delete-request", applicationId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject data deletion for non-rejected application")
    void shouldRejectDataDeletionForNonRejectedApplication() throws Exception {
        // Given - Submitted application (not rejected)
        UUID applicationId = createSubmittedApplication();

        // When/Then - Should fail because only rejected applications can be deleted
        performAsyncAsApplicant(applicationId,
                delete("/api/v1/applicant/applications/{id}/delete-request", applicationId))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("Admin should process data deletion and anonymize data")
    void adminShouldProcessDataDeletion() throws Exception {
        // Given - Application marked for deletion with retention period passed
        UUID applicationId = createRejectedApplication();
        OnboardingApplication application = onboardingRepository.findById(applicationId).orElseThrow();
        application.markForDeletion();

        // Set retention period to past (simulate 91 days have passed)
        setPrivateField(application, "dataRetentionUntil", LocalDate.now().minusDays(1).atStartOfDay());

        application.clearEvents();
        onboardingRepository.save(application);

        // When - Admin processes deletion (clean role-based auth!)
        performAsyncAsAdmin(post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then - Verify data is anonymized
        OnboardingApplication anonymized = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(anonymized.getFirstName()).isEqualTo("DELETED");
        assertThat(anonymized.getLastName()).isEqualTo("DELETED");
        assertThat(anonymized.getEmail()).isEqualTo("deleted@anonymized.local");
    }

    @Test
    @DisplayName("Should reject process deletion without admin role")
    void shouldRejectProcessDeletionWithoutAdminRole() throws Exception {
        // Given
        UUID applicationId = createRejectedApplication();

        // When/Then - Compliance officer CANNOT process deletion
        performAsyncAsComplianceOfficer(
                post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Complete GDPR workflow: Request -> Admin Process -> Anonymize")
    void shouldCompleteFullGdprDeletionWorkflow() throws Exception {
        // Given - Create rejected application
        UUID applicationId = createRejectedApplication();

        // Step 1: Applicant requests deletion
        performAsyncAsApplicant(applicationId,
                delete("/api/v1/applicant/applications/{id}/delete-request", applicationId))
                .andExpect(status().isAccepted());

        // Verify marked for deletion
        OnboardingApplication afterRequest = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(afterRequest.isMarkedForDeletion()).isTrue();
        assertThat(afterRequest.getFirstName()).isNotEqualTo("DELETED"); // Not yet anonymized

        // Set retention period to past (simulate retention period has passed)
        setPrivateField(afterRequest, "dataRetentionUntil", LocalDate.now().minusDays(1).atStartOfDay());
        afterRequest.clearEvents();
        onboardingRepository.save(afterRequest);

        // Step 2: Admin processes deletion
        performAsyncAsAdmin(post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isOk());

        // Step 3: Verify complete anonymization
        OnboardingApplication afterProcessing = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(afterProcessing.isMarkedForDeletion()).isTrue();
        assertThat(afterProcessing.getFirstName()).isEqualTo("DELETED");
        assertThat(afterProcessing.getSocialSecurityNumber()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("Should demonstrate role-based access control")
    void shouldDemonstrateRoleBasedAccessControl() throws Exception {
        UUID applicationId = createRejectedApplication();

        // Mark for deletion first and set retention period to past
        OnboardingApplication application = onboardingRepository.findById(applicationId).orElseThrow();
        application.markForDeletion();

        // Set retention period to past
        setPrivateField(application, "dataRetentionUntil", LocalDate.now().minusDays(1).atStartOfDay());

        application.clearEvents();
        onboardingRepository.save(application);

        // Try to process deletion with different roles
        // Anonymous - should fail (401)
        performAsAnonymous(post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isUnauthorized());

        // Applicant - should fail (403 Forbidden)
        performAsyncAsApplicant(applicationId,
                post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isForbidden());

        // Compliance Officer - should fail (403 Forbidden)
        performAsyncAsComplianceOfficer(
                post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isForbidden());

        // Admin - should succeed (200 OK)
        performAsyncAsAdmin(post("/api/v1/admin/applications/{id}/process-deletion", applicationId))
                .andExpect(status().isOk());
    }

    // ==================== Helper Methods ====================

    private UUID createRejectedApplication() {
        Address address = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        OnboardingApplication application = new OnboardingApplication(
                UUID.randomUUID(),
                "Alice",
                "Williams",
                Gender.FEMALE,
                LocalDate.of(1995, 8, 25),
                "+31612345681",
                "alice.williams@example.com",
                "NL",
                address,
                "444555666"
        );

        // Add required consents
        application.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.DATA_PROCESSING,
                true,
                "127.0.0.1",
                "I consent to data processing",
                "1.0"
        ));

        application.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.TERMS_AND_CONDITIONS,
                true,
                "127.0.0.1",
                "I accept terms and conditions",
                "1.0"
        ));

        // Add verification and documents
        application.markEmailAsVerified();

        ApplicationDocument passport = new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PASSPORT,
                "/storage/passport.jpg",
                "image/jpeg",
                1024L
        );
        application.addDocument(passport);

        ApplicationDocument photo = new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PHOTO,
                "/storage/photo.jpg",
                "image/jpeg",
                2048L
        );
        application.addDocument(photo);

        application.submit();
        application.assignTo("officer@abc.nl");
        application.verifyDocuments("officer@abc.nl");
        application.reject("Failed verification");
        application.clearEvents();

        return onboardingRepository.save(application).getId();
    }

    private UUID createSubmittedApplication() {
        Address address = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        OnboardingApplication application = new OnboardingApplication(
                UUID.randomUUID(),
                "Bob",
                "Johnson",
                Gender.MALE,
                LocalDate.of(1988, 5, 10),
                "+31612345680",
                "bob.johnson@example.com",
                "NL",
                address,
                "111222333"
        );

        application.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.DATA_PROCESSING,
                true,
                "127.0.0.1",
                "I consent",
                "1.0"
        ));

        application.addConsent(new ConsentRecord(
                UUID.randomUUID(),
                ConsentType.TERMS_AND_CONDITIONS,
                true,
                "127.0.0.1",
                "I accept",
                "1.0"
        ));

        application.markEmailAsVerified();

        ApplicationDocument passport = new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PASSPORT,
                "/storage/passport.jpg",
                "image/jpeg",
                1024L
        );
        application.addDocument(passport);

        ApplicationDocument photo = new ApplicationDocument(
                UUID.randomUUID(),
                DocumentType.PHOTO,
                "/storage/photo.jpg",
                "image/jpeg",
                2048L
        );
        application.addDocument(photo);

        application.submit();
        application.clearEvents();

        return onboardingRepository.save(application).getId();
    }

    /**
     * Helper method to set private fields using reflection.
     * Used to simulate retention period expiry for GDPR tests.
     */
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    /**
     * Find a field in the class hierarchy.
     */
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }
}
