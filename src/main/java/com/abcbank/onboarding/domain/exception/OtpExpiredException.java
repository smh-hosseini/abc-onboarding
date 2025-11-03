package com.abcbank.onboarding.domain.exception;

/**
 * Exception thrown when attempting to verify an expired OTP.
 * OTPs expire after 10 minutes by default.
 */
public class OtpExpiredException extends DomainException {

    public OtpExpiredException(String message) {
        super(message);
    }

    public OtpExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
