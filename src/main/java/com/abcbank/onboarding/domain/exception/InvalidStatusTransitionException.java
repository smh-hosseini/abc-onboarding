package com.abcbank.onboarding.domain.exception;

/**
 * Exception thrown when an invalid state transition is attempted.
 * For example, trying to submit an application without uploading documents.
 */
public class InvalidStatusTransitionException extends DomainException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
