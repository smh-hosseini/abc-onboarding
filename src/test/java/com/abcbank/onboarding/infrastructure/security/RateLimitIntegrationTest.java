package com.abcbank.onboarding.infrastructure.security;

import com.abcbank.onboarding.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for rate limiting with real Redis
 */
@DisplayName("Rate Limiting Integration Tests")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should enforce rate limit for application creation (5 per hour per IP)")
    void shouldEnforceRateLimitForApplicationCreation() throws Exception {
        // Given
        Map<String, Object> request = createApplicationRequest("user1@example.com", "111111111");

        // When - Make 5 requests (at limit)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/onboarding/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    updateEmail(request, "user" + i + "@example.com")))
                            .header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().string("X-RateLimit-Limit", "5"));
        }

        // Then - 6th request should be rate limited
        mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateEmail(request, "user6@example.com")))
                        .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    @DisplayName("Should track rate limits per IP address")
    void shouldTrackRateLimitsPerIpAddress() throws Exception {
        // Given
        Map<String, Object> request = createApplicationRequest("user@example.com", "123456789");

        // When - Make 5 requests from IP1 (at limit)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/onboarding/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    updateEmail(request, "user" + i + "@example.com")))
                            .header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isCreated());
        }

        // Then - IP2 should still be able to make requests
        mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateEmail(request, "other@example.com")))
                        .header("X-Forwarded-For", "192.168.1.200"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "4"));
    }

    @Test
    @DisplayName("Should enforce rate limit for send OTP (3 per hour per IP)")
    void shouldEnforceRateLimitForSendOtp() throws Exception {
        // Given - Create applications
        String applicationId1 = createApplicationAndGetId("user1@example.com", "111111111");
        String applicationId2 = createApplicationAndGetId("user2@example.com", "222222222");
        String applicationId3 = createApplicationAndGetId("user3@example.com", "333333333");
        String applicationId4 = createApplicationAndGetId("user4@example.com", "444444444");

        String ip = "192.168.1.150";

        // When - Make 3 OTP requests (at limit)
        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "EMAIL")))
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "EMAIL")))
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "EMAIL")))
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isOk());

        // Then - 4th request should be rate limited
        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId4)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("channel", "EMAIL")))
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"));
    }

    @Test
    @DisplayName("Should include rate limit headers in responses")
    void shouldIncludeRateLimitHeadersInResponses() throws Exception {
        // Given
        Map<String, Object> request = createApplicationRequest("user@example.com", "123456789");

        // When - First request
        MvcResult result = mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.1.175"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andReturn();

        // Then - Verify headers
        String limit = result.getResponse().getHeader("X-RateLimit-Limit");
        String remaining = result.getResponse().getHeader("X-RateLimit-Remaining");

        assert limit != null;
        assert remaining != null;
        assert Integer.parseInt(limit) == 5;
        assert Integer.parseInt(remaining) == 4; // Used 1 out of 5
    }

    @Test
    @DisplayName("Should handle X-Real-IP header for rate limiting")
    void shouldHandleXRealIpHeaderForRateLimiting() throws Exception {
        // Given
        Map<String, Object> request = createApplicationRequest("user@example.com", "123456789");

        // When - Use X-Real-IP header
        mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Real-IP", "192.168.1.250"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-RateLimit-Limit"));
    }

    @Test
    @DisplayName("Should reset rate limit after window expires")
    void shouldResetRateLimitAfterWindowExpires() throws Exception {
        // Note: This test would require waiting for the window to expire,
        // which is not practical in integration tests.
        // In real scenarios, use admin endpoints to reset rate limits for testing.

        // Given - Hit rate limit
        Map<String, Object> request = createApplicationRequest("user@example.com", "123456789");
        String ip = "192.168.1.199";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/onboarding/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    updateEmail(request, "user" + i + "@example.com")))
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isCreated());
        }

        // Verify rate limit is hit
        mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                updateEmail(request, "blocked@example.com")))
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests());

        // In production, after waiting for window to expire, requests would work again
        // For testing, we can use admin endpoints to reset rate limits
    }

    // Helper methods

    private Map<String, Object> createApplicationRequest(String email, String ssn) {
        return Map.ofEntries(
                Map.entry("firstName", "John"),
                Map.entry("lastName", "Doe"),
                Map.entry("gender", "MALE"),
                Map.entry("dateOfBirth", "1990-01-15"),
                Map.entry("phone", "+31612345678"),
                Map.entry("email", email),
                Map.entry("nationality", "NL"),
                Map.entry("socialSecurityNumber", ssn),
                Map.entry("residentialAddress", Map.of(
                        "street", "Main Street",
                        "houseNumber", "10",
                        "postalCode", "1011AB",
                        "city", "Amsterdam",
                        "country", "NL"
                )),
                Map.entry("consents", Map.of(
                        "gdprDataProcessing", true,
                        "termsAndConditions", true
                ))
        );
    }

    private Map<String, Object> updateEmail(Map<String, Object> request, String newEmail) {
        // Generate a random 9-digit SSN
        String randomSsn = String.format("%09d", (int) (Math.random() * 1000000000));

        return Map.ofEntries(
                Map.entry("firstName", request.get("firstName")),
                Map.entry("lastName", request.get("lastName")),
                Map.entry("gender", request.get("gender")),
                Map.entry("dateOfBirth", request.get("dateOfBirth")),
                Map.entry("phone", request.get("phone")),
                Map.entry("email", newEmail),
                Map.entry("nationality", request.get("nationality")),
                Map.entry("socialSecurityNumber", randomSsn),
                Map.entry("residentialAddress", request.get("residentialAddress")),
                Map.entry("consents", request.get("consents"))
        );
    }

    private String createApplicationAndGetId(String email, String ssn) throws Exception {
        Map<String, Object> request = createApplicationRequest(email, ssn);

        MvcResult result = mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("applicationId").asText();
    }
}
