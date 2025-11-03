package com.abcbank.onboarding.domain.exception;

/**
 * Exception thrown when maximum OTP verification attempts have been exceeded.
 * Default maximum is 3 attempts.
 */
public class OtpMaxAttemptsException extends DomainException {

    public OtpMaxAttemptsException(String message) {
        super(message);
    }

    public OtpMaxAttemptsException(String message, Throwable cause) {
        super(message, cause);
    }
}
