package com.abcbank.onboarding.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain service for OTP (One-Time Password) generation and verification.
 * Handles secure OTP generation, BCrypt hashing, and verification logic.
 *
 * Security Features:
 * - Uses SecureRandom for cryptographically strong random number generation
 * - BCrypt hashing with adaptive cost factor for secure storage
 * - 10-minute expiry window to limit attack surface
 * - Thread-safe implementation
 */
@Slf4j
@Service
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor injection of PasswordEncoder (BCrypt).
     *
     * @param passwordEncoder Spring Security BCrypt password encoder
     */
    public OtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder cannot be null");
    }

    /**
     * Generates a cryptographically secure 6-digit OTP.
     * Uses SecureRandom to ensure unpredictability and prevent OTP guessing attacks.
     *
     * @return 6-digit OTP as a String (e.g., "123456", "000789")
     */
    public String generateOtp() {
        int otp = secureRandom.nextInt(1_000_000);
        String otpString = String.format("%06d", otp);

        log.debug("Generated new OTP (length: {})", OTP_LENGTH);

        return otpString;
    }

    /**
     * Hashes the OTP using BCrypt for secure storage.
     * BCrypt is a computationally expensive algorithm that provides protection against
     * brute-force attacks through adaptive cost factor and automatic salting.
     *
     * @param otp the plain-text OTP to hash
     * @return BCrypt hash of the OTP
     * @throws IllegalArgumentException if otp is null or empty
     */
    public String hashOtp(String otp) {
        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP cannot be null or empty");
        }

        if (otp.length() != OTP_LENGTH) {
            log.warn("Attempting to hash OTP with invalid length: {}", otp.length());
            throw new IllegalArgumentException("OTP must be exactly " + OTP_LENGTH + " digits");
        }

        String hash = passwordEncoder.encode(otp);

        log.debug("OTP hashed successfully using BCrypt");

        return hash;
    }

    /**
     * Verifies a provided OTP against the stored BCrypt hash.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param providedOtp the OTP provided by the user
     * @param hashedOtp the BCrypt hash stored in the database
     * @return true if the OTP matches, false otherwise
     * @throws IllegalArgumentException if either parameter is null or empty
     */
    public boolean verifyOtp(String providedOtp, String hashedOtp) {
        if (providedOtp == null || providedOtp.trim().isEmpty()) {
            log.warn("Attempted OTP verification with null or empty provided OTP");
            throw new IllegalArgumentException("Provided OTP cannot be null or empty");
        }

        if (hashedOtp == null || hashedOtp.trim().isEmpty()) {
            log.warn("Attempted OTP verification with null or empty hashed OTP");
            throw new IllegalArgumentException("Hashed OTP cannot be null or empty");
        }

        boolean matches = passwordEncoder.matches(providedOtp, hashedOtp);

        if (matches) {
            log.debug("OTP verification successful");
        } else {
            log.debug("OTP verification failed - mismatch");
        }

        return matches;
    }

    /**
     * Generates the expiry time for an OTP.
     * OTPs are valid for 10 minutes from generation time.
     *
     * @return LocalDateTime representing when the OTP expires
     */
    public LocalDateTime generateOtpExpiryTime() {
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        log.debug("Generated OTP expiry time: {} ({} minutes from now)",
                expiryTime, OTP_EXPIRY_MINUTES);

        return expiryTime;
    }

    /**
     * Checks if an OTP has expired.
     *
     * @param expiryTime the expiry time to check
     * @return true if the OTP has expired, false otherwise
     */
    public boolean isOtpExpired(LocalDateTime expiryTime) {
        if (expiryTime == null) {
            log.warn("Checking expiry for null expiry time");
            return true;
        }

        boolean expired = LocalDateTime.now().isAfter(expiryTime);

        if (expired) {
            log.debug("OTP has expired (expiry time: {})", expiryTime);
        }

        return expired;
    }

    /**
     * Gets the OTP expiry duration in minutes.
     *
     * @return OTP validity period in minutes
     */
    public int getOtpExpiryMinutes() {
        return OTP_EXPIRY_MINUTES;
    }
}
