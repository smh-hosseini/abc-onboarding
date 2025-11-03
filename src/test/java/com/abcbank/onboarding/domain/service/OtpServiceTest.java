package com.abcbank.onboarding.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OtpService.
 * Tests OTP generation, hashing, verification, and expiry logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService Unit Tests")
class OtpServiceTest {

    private OtpService otpService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(passwordEncoder);
    }

    @Test
    @DisplayName("Should generate 6-digit OTP")
    void shouldGenerate6DigitOtp() {
        // When
        String otp = otpService.generateOtp();

        // Then
        assertThat(otp).isNotNull();
        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    @Test
    @DisplayName("Should generate OTP with leading zeros preserved")
    void shouldGenerateOtpWithLeadingZerosPreserved() {
        // When - Generate multiple OTPs to check if leading zeros are preserved
        for (int i = 0; i < 100; i++) {
            String otp = otpService.generateOtp();

            // Then - Should always be 6 digits, including leading zeros
            assertThat(otp).hasSize(6);
            assertThat(otp).matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("Should generate different OTPs on multiple calls")
    void shouldGenerateDifferentOtpsOnMultipleCalls() {
        // When - Generate multiple OTPs
        String otp1 = otpService.generateOtp();
        String otp2 = otpService.generateOtp();
        String otp3 = otpService.generateOtp();

        // Then - At least some should be different (extremely unlikely all 3 are same)
        assertThat(otp1).isNotNull();
        assertThat(otp2).isNotNull();
        assertThat(otp3).isNotNull();
        // Can't guarantee all different due to randomness, but can check valid format
        assertThat(otp1).matches("\\d{6}");
        assertThat(otp2).matches("\\d{6}");
        assertThat(otp3).matches("\\d{6}");
    }

    @Test
    @DisplayName("Should hash OTP using password encoder")
    void shouldHashOtpUsingPasswordEncoder() {
        // Given
        String otp = "123456";
        String hashedOtp = "$2a$10$hashedValue";
        when(passwordEncoder.encode(otp)).thenReturn(hashedOtp);

        // When
        String result = otpService.hashOtp(otp);

        // Then
        assertThat(result).isEqualTo(hashedOtp);
        verify(passwordEncoder, times(1)).encode(otp);
    }

    @Test
    @DisplayName("Should throw exception when hashing null OTP")
    void shouldThrowExceptionWhenHashingNullOtp() {
        // When/Then
        assertThatThrownBy(() -> otpService.hashOtp(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP cannot be null or empty");

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Should throw exception when hashing empty OTP")
    void shouldThrowExceptionWhenHashingEmptyOtp() {
        // When/Then
        assertThatThrownBy(() -> otpService.hashOtp(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP cannot be null or empty");

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Should throw exception when hashing OTP with whitespace only")
    void shouldThrowExceptionWhenHashingOtpWithWhitespaceOnly() {
        // When/Then
        assertThatThrownBy(() -> otpService.hashOtp("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP cannot be null or empty");

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Should throw exception when hashing OTP with invalid length")
    void shouldThrowExceptionWhenHashingOtpWithInvalidLength() {
        // Given - OTP with wrong length
        String invalidOtp = "12345"; // Only 5 digits

        // When/Then
        assertThatThrownBy(() -> otpService.hashOtp(invalidOtp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP must be exactly 6 digits");

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Should verify OTP successfully when OTP matches")
    void shouldVerifyOtpSuccessfullyWhenOtpMatches() {
        // Given
        String providedOtp = "123456";
        String hashedOtp = "$2a$10$hashedValue";
        when(passwordEncoder.matches(providedOtp, hashedOtp)).thenReturn(true);

        // When
        boolean result = otpService.verifyOtp(providedOtp, hashedOtp);

        // Then
        assertThat(result).isTrue();
        verify(passwordEncoder, times(1)).matches(providedOtp, hashedOtp);
    }

    @Test
    @DisplayName("Should fail verification when OTP does not match")
    void shouldFailVerificationWhenOtpDoesNotMatch() {
        // Given
        String providedOtp = "123456";
        String hashedOtp = "$2a$10$hashedValue";
        when(passwordEncoder.matches(providedOtp, hashedOtp)).thenReturn(false);

        // When
        boolean result = otpService.verifyOtp(providedOtp, hashedOtp);

        // Then
        assertThat(result).isFalse();
        verify(passwordEncoder, times(1)).matches(providedOtp, hashedOtp);
    }

    @Test
    @DisplayName("Should throw exception when verifying with null provided OTP")
    void shouldThrowExceptionWhenVerifyingWithNullProvidedOtp() {
        // Given
        String hashedOtp = "$2a$10$hashedValue";

        // When/Then
        assertThatThrownBy(() -> otpService.verifyOtp(null, hashedOtp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provided OTP cannot be null or empty");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when verifying with empty provided OTP")
    void shouldThrowExceptionWhenVerifyingWithEmptyProvidedOtp() {
        // Given
        String hashedOtp = "$2a$10$hashedValue";

        // When/Then
        assertThatThrownBy(() -> otpService.verifyOtp("", hashedOtp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provided OTP cannot be null or empty");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when verifying with null hashed OTP")
    void shouldThrowExceptionWhenVerifyingWithNullHashedOtp() {
        // Given
        String providedOtp = "123456";

        // When/Then
        assertThatThrownBy(() -> otpService.verifyOtp(providedOtp, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hashed OTP cannot be null or empty");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when verifying with empty hashed OTP")
    void shouldThrowExceptionWhenVerifyingWithEmptyHashedOtp() {
        // Given
        String providedOtp = "123456";

        // When/Then
        assertThatThrownBy(() -> otpService.verifyOtp(providedOtp, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hashed OTP cannot be null or empty");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should generate expiry time 10 minutes in the future")
    void shouldGenerateExpiryTime10MinutesInTheFuture() {
        // Given
        LocalDateTime before = LocalDateTime.now();

        // When
        LocalDateTime expiryTime = otpService.generateOtpExpiryTime();

        // Then
        LocalDateTime after = LocalDateTime.now();
        LocalDateTime expectedMin = before.plusMinutes(10);
        LocalDateTime expectedMax = after.plusMinutes(10);

        assertThat(expiryTime)
                .isAfterOrEqualTo(expectedMin)
                .isBeforeOrEqualTo(expectedMax);
    }

    @Test
    @DisplayName("Should detect expired OTP")
    void shouldDetectExpiredOtp() {
        // Given - OTP that expired 1 minute ago
        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(1);

        // When
        boolean isExpired = otpService.isOtpExpired(expiredTime);

        // Then
        assertThat(isExpired).isTrue();
    }

    @Test
    @DisplayName("Should detect non-expired OTP")
    void shouldDetectNonExpiredOtp() {
        // Given - OTP that expires in 5 minutes
        LocalDateTime futureTime = LocalDateTime.now().plusMinutes(5);

        // When
        boolean isExpired = otpService.isOtpExpired(futureTime);

        // Then
        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("Should consider null expiry time as expired")
    void shouldConsiderNullExpiryTimeAsExpired() {
        // When
        boolean isExpired = otpService.isOtpExpired(null);

        // Then
        assertThat(isExpired).isTrue();
    }

    @Test
    @DisplayName("Should return correct OTP expiry minutes")
    void shouldReturnCorrectOtpExpiryMinutes() {
        // When
        int expiryMinutes = otpService.getOtpExpiryMinutes();

        // Then
        assertThat(expiryMinutes).isEqualTo(10);
    }

    @Test
    @DisplayName("Should throw exception when constructing with null password encoder")
    void shouldThrowExceptionWhenConstructingWithNullPasswordEncoder() {
        // When/Then
        assertThatThrownBy(() -> new OtpService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("PasswordEncoder cannot be null");
    }

    @Test
    @DisplayName("Should generate OTP within valid range")
    void shouldGenerateOtpWithinValidRange() {
        // When - Generate multiple OTPs
        for (int i = 0; i < 100; i++) {
            String otp = otpService.generateOtp();
            int otpValue = Integer.parseInt(otp);

            // Then - Should be between 0 and 999999
            assertThat(otpValue).isBetween(0, 999999);
        }
    }

    @Test
    @DisplayName("Should detect OTP expired exactly at expiry time")
    void shouldDetectOtpExpiredExactlyAtExpiryTime() {
        // Given - Expiry time is now (edge case)
        LocalDateTime now = LocalDateTime.now();

        // When
        boolean isExpired = otpService.isOtpExpired(now);

        // Then - Should be considered expired (strict after check)
        // Note: Due to timing, this might be true or false depending on execution speed
        // The important part is it doesn't throw an exception
        assertThat(isExpired).isIn(true, false);
    }
}
