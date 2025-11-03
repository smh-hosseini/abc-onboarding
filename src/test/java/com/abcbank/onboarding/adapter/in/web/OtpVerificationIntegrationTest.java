package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.AbstractIntegrationTest;
import com.abcbank.onboarding.adapter.in.web.dto.OnboardingRequest;
import com.abcbank.onboarding.adapter.in.web.dto.AddressRequest;
import com.abcbank.onboarding.adapter.in.web.dto.SendOtpRequest;
import com.abcbank.onboarding.adapter.in.web.dto.OtpVerificationRequest;
import com.abcbank.onboarding.domain.model.Gender;
import com.abcbank.onboarding.domain.model.OtpVerification;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.port.out.OtpVerificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OTP verification flow
 */
@DisplayName("OTP Verification Integration Tests")
class OtpVerificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OnboardingRepository onboardingRepository;

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should send OTP successfully to EMAIL")
    void shouldSendOtpSuccessfully() throws Exception {
        // Given - Create application first
        UUID applicationId = createApplication();
        SendOtpRequest request = new SendOtpRequest(SendOtpRequest.OtpChannel.EMAIL);

        // When - Send OTP (using async dispatch to wait for CompletableFuture to complete)
        performAsync(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(result -> System.out.println("Send OTP Status: " + result.getResponse().getStatus() + ", Body: " + result.getResponse().getContentAsString()))
                .andExpect(status().isAccepted())  // Changed from isOk() to isAccepted() (202)
                .andExpect(jsonPath("$.message").value("OTP sent successfully to your email"))
                .andExpect(jsonPath("$.expiresIn").value(600));

        // Then - Verify no OTP in application (moved to separate table)
        OnboardingApplication application = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(application.isEmailVerified()).isFalse();
        assertThat(application.isPhoneVerified()).isFalse();
    }

    @Test
    @DisplayName("Should verify OTP successfully with correct code")
    void shouldVerifyOtpSuccessfully() throws Exception {
        // Given - Create application and set known OTP
        UUID applicationId = createApplication();
        String otp = "123456";
        String otpHash = passwordEncoder.encode(otp);

        // Create OTP verification record
        OtpVerification otpVerification = new OtpVerification(
                UUID.randomUUID(),
                applicationId,
                OtpVerification.OtpChannel.EMAIL,
                otpHash,
                LocalDateTime.now().plusMinutes(10)
        );
        otpVerificationRepository.save(otpVerification);

        OtpVerificationRequest request = new OtpVerificationRequest(otp, SendOtpRequest.OtpChannel.EMAIL);

        // When - Verify OTP
        MvcResult result = mockMvc.perform(post("/api/v1/onboarding/applications/{id}/verify-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        // Then - Verify application status changed and JWT token returned
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

        assertThat(response.get("accessToken")).isNotNull();
        assertThat(response.get("accessToken").toString()).isNotEmpty();

        // Verify application status and email verification
        OnboardingApplication updatedApp = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(updatedApp.getStatus().name()).isEqualTo("OTP_VERIFIED");
        assertThat(updatedApp.isEmailVerified()).isTrue();
        assertThat(updatedApp.isPhoneVerified()).isFalse();
    }

    @Test
    @DisplayName("Should reject OTP verification with incorrect code")
    void shouldRejectOtpVerificationWithIncorrectCode() throws Exception {
        // Given - Create application and set OTP
        UUID applicationId = createApplication();
        String correctOtp = "123456";
        String incorrectOtp = "999999";
        String otpHash = passwordEncoder.encode(correctOtp);

        OtpVerification otpVerification = new OtpVerification(
                UUID.randomUUID(),
                applicationId,
                OtpVerification.OtpChannel.EMAIL,
                otpHash,
                LocalDateTime.now().plusMinutes(10)
        );
        otpVerificationRepository.save(otpVerification);

        OtpVerificationRequest request = new OtpVerificationRequest(incorrectOtp, SendOtpRequest.OtpChannel.EMAIL);

        // When/Then
        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/verify-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"))
                .andExpect(jsonPath("$.detail").value("Invalid OTP. 2 attempt(s) remaining."));

        // Verify OTP attempt was incremented
        OtpVerification updatedOtp = otpVerificationRepository.findById(otpVerification.getId()).orElseThrow();
        assertThat(updatedOtp.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject expired OTP")
    void shouldRejectExpiredOtp() throws Exception {
        // Given - Create application with expired OTP
        UUID applicationId = createApplication();
        String otp = "123456";
        String otpHash = passwordEncoder.encode(otp);

        OtpVerification otpVerification = new OtpVerification(
                UUID.randomUUID(),
                applicationId,
                OtpVerification.OtpChannel.EMAIL,
                otpHash,
                LocalDateTime.now().minusMinutes(1) // Expired
        );
        otpVerificationRepository.save(otpVerification);

        OtpVerificationRequest request = new OtpVerificationRequest(otp, SendOtpRequest.OtpChannel.EMAIL);

        // When/Then
        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/verify-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("OTP has expired. Please request a new one."));
    }

    @Test
    @DisplayName("Should block OTP verification after max attempts")
    void shouldBlockOtpVerificationAfterMaxAttempts() throws Exception {
        // Given - Create application with max attempts reached
        UUID applicationId = createApplication();
        String otp = "123456";
        String otpHash = passwordEncoder.encode(otp);

        OtpVerification otpVerification = new OtpVerification(
                UUID.randomUUID(),
                applicationId,
                OtpVerification.OtpChannel.EMAIL,
                otpHash,
                LocalDateTime.now().plusMinutes(10),
                3, // 3 attempts already
                OtpVerification.OtpStatus.PENDING,
                LocalDateTime.now(),
                null
        );
        otpVerificationRepository.save(otpVerification);

        OtpVerificationRequest request = new OtpVerificationRequest("999999", SendOtpRequest.OtpChannel.EMAIL);

        // When/Then
        mockMvc.perform(post("/api/v1/onboarding/applications/{id}/verify-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
                .andExpect(jsonPath("$.detail").value("Maximum OTP verification attempts exceeded. Please request a new OTP."));
    }

    @Test
    @DisplayName("Should resend OTP successfully")
    void shouldResendOtpSuccessfully() throws Exception {
        // Given - Create application and send OTP first time
        UUID applicationId = createApplication();
        SendOtpRequest request = new SendOtpRequest(SendOtpRequest.OtpChannel.EMAIL);

        performAsync(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        // Wait a bit to ensure different OTP
        Thread.sleep(100);

        // When - Resend OTP
        performAsync(post("/api/v1/onboarding/applications/{id}/send-otp", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("OTP sent successfully to your email"))
                .andExpect(jsonPath("$.expiresIn").value(600));

        // Then - Verify new OTP verification record was created (verification status should still be false)
        OnboardingApplication updatedApp = onboardingRepository.findById(applicationId).orElseThrow();
        assertThat(updatedApp.isEmailVerified()).isFalse();
    }

    /**
     * Helper method to create a test application
     */
    private UUID createApplication() throws Exception {
        OnboardingRequest request = getOnboardingRequest();

        MvcResult result = mockMvc.perform(post("/api/v1/onboarding/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(result1 -> {
                    if (result1.getResponse().getStatus() != 201) {
                        System.out.println("ERROR creating app - Status: " + result1.getResponse().getStatus() + ", Body: " + result1.getResponse().getContentAsString());
                    }
                })
                .andExpect(status().isCreated())
                .andReturn();

        String applicationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("applicationId").asText();

        return UUID.fromString(applicationId);
    }

    private static @NotNull OnboardingRequest getOnboardingRequest() {
        AddressRequest address = new AddressRequest(
                "Main Street",
                "10",
                "1011AB",
                "Amsterdam",
                "NL"
        );

        return new OnboardingRequest(
                "John",
                "Doe",
                Gender.MALE,
                LocalDate.of(1990, 1, 15),
                "+31612345678",
                "john.doe@example.com",
                "NL",
                address,
                "111222333"
        );
    }
}
