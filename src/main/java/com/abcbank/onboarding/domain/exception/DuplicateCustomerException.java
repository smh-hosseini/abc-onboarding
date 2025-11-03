package com.abcbank.onboarding.domain.exception;

/**
 * Exception thrown when a customer already exists in the system.
 * Duplicate detection is based on SSN, email, or phone number.
 */
public class DuplicateCustomerException extends DomainException {

    private final String duplicateField;

    public DuplicateCustomerException(String message, String duplicateField) {
        super(message);
        this.duplicateField = duplicateField;
    }

    public String getDuplicateField() {
        return duplicateField;
    }
}
