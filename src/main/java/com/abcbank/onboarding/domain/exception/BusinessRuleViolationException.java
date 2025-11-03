package com.abcbank.onboarding.domain.exception;

/**
 * Exception thrown when a business rule is violated.
 * Generic exception for business logic constraints.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
