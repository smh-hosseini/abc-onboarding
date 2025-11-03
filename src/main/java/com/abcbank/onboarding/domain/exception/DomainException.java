package com.abcbank.onboarding.domain.exception;

/**
 * Base exception for all domain-level exceptions.
 * Domain exceptions represent business rule violations.
 */
public abstract class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
