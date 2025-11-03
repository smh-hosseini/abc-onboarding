package com.abcbank.onboarding.domain.service;

import com.abcbank.onboarding.domain.exception.DuplicateCustomerException;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain service for detecting duplicate customer records.
 * Prevents duplicate registrations based on SSN, email, and phone number.
 *
 * Business Rules:
 * - SSN (Social Security Number) must be unique across all applications
 * - Email must be unique across all applications
 * - Phone number must be unique across all applications
 * - Any duplicate triggers a DuplicateCustomerException
 *
 * This service enforces data integrity and compliance with regulatory requirements
 * that mandate unique identification of customers.
 */
@Slf4j
@Service
public class DuplicateDetectionService {

    /**
     * Enum representing the type of duplicate detected.
     * Used for analytics, logging, and providing specific error messages.
     */
    public enum DuplicateType {
        /**
         * Social Security Number already exists in the system
         */
        SSN("Social Security Number"),

        /**
         * Email address already exists in the system
         */
        EMAIL("Email"),

        /**
         * Phone number already exists in the system
         */
        PHONE("Phone Number"),

        /**
         * No duplicates detected
         */
        NONE("None");

        private final String displayName;

        DuplicateType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Checks for duplicate customer records and throws an exception if any are found.
     * This is the primary method for duplicate detection during customer onboarding.
     *
     * @param ssn the Social Security Number to check
     * @param email the email address to check
     * @param phone the phone number to check
     * @param repository the repository to query for existing records
     * @throws DuplicateCustomerException if any duplicate is found
     * @throws IllegalArgumentException if any parameter is null
     */
    public void checkDuplicates(String ssn, String email, String phone, OnboardingRepository repository) {
        Objects.requireNonNull(ssn, "SSN cannot be null");
        Objects.requireNonNull(email, "Email cannot be null");
        Objects.requireNonNull(phone, "Phone cannot be null");
        Objects.requireNonNull(repository, "Repository cannot be null");

        log.debug("Checking for duplicates: email={}, phone={}", email, phone);

        DuplicateType duplicateType = detectDuplicateType(ssn, email, phone, repository);

        if (duplicateType != DuplicateType.NONE) {
            String errorMessage = String.format(
                    "Customer with this %s already exists in the system",
                    duplicateType.getDisplayName()
            );

            log.warn("Duplicate customer detected: type={}, field={}", duplicateType, duplicateType.getDisplayName());

            throw new DuplicateCustomerException(errorMessage, duplicateType.name());
        }

        log.debug("No duplicates found - validation passed");
    }

    /**
     * Detects the type of duplicate without throwing an exception.
     * Useful for analytics, reporting, and conditional logic where you need to know
     * the specific type of duplicate without interrupting the flow.
     *
     * Detection Priority:
     * 1. SSN (highest priority - most definitive identifier)
     * 2. Email
     * 3. Phone
     *
     * @param ssn the Social Security Number to check
     * @param email the email address to check
     * @param phone the phone number to check
     * @param repository the repository to query for existing records
     * @return DuplicateType enum indicating which field is duplicated, or NONE
     * @throws IllegalArgumentException if any parameter is null
     */
    public DuplicateType detectDuplicateType(String ssn, String email, String phone, OnboardingRepository repository) {
        Objects.requireNonNull(ssn, "SSN cannot be null");
        Objects.requireNonNull(email, "Email cannot be null");
        Objects.requireNonNull(phone, "Phone cannot be null");
        Objects.requireNonNull(repository, "Repository cannot be null");

        // Check SSN first (highest priority)
        if (repository.existsBySocialSecurityNumber(ssn)) {
            log.info("Duplicate detected: SSN already exists");
            return DuplicateType.SSN;
        }

        // Check Email
        if (repository.existsByEmail(email)) {
            log.info("Duplicate detected: Email already exists");
            return DuplicateType.EMAIL;
        }

        // Check Phone
        if (repository.existsByPhone(phone)) {
            log.info("Duplicate detected: Phone number already exists");
            return DuplicateType.PHONE;
        }

        return DuplicateType.NONE;
    }

    /**
     * Detects all types of duplicates present (can detect multiple).
     * Useful for comprehensive validation and detailed error reporting.
     *
     * @param ssn the Social Security Number to check
     * @param email the email address to check
     * @param phone the phone number to check
     * @param repository the repository to query for existing records
     * @return List of all DuplicateTypes found (empty list if no duplicates)
     * @throws IllegalArgumentException if any parameter is null
     */
    public List<DuplicateType> detectAllDuplicates(String ssn, String email, String phone, OnboardingRepository repository) {
        Objects.requireNonNull(ssn, "SSN cannot be null");
        Objects.requireNonNull(email, "Email cannot be null");
        Objects.requireNonNull(phone, "Phone cannot be null");
        Objects.requireNonNull(repository, "Repository cannot be null");

        List<DuplicateType> duplicates = new ArrayList<>();

        if (repository.existsBySocialSecurityNumber(ssn)) {
            duplicates.add(DuplicateType.SSN);
        }

        if (repository.existsByEmail(email)) {
            duplicates.add(DuplicateType.EMAIL);
        }

        if (repository.existsByPhone(phone)) {
            duplicates.add(DuplicateType.PHONE);
        }

        if (!duplicates.isEmpty()) {
            log.info("Multiple duplicates detected: {}", duplicates);
        }

        return duplicates;
    }

    /**
     * Checks if any duplicates exist without throwing an exception.
     * Convenient boolean check for conditional logic.
     *
     * @param ssn the Social Security Number to check
     * @param email the email address to check
     * @param phone the phone number to check
     * @param repository the repository to query for existing records
     * @return true if any duplicate exists, false otherwise
     */
    public boolean hasDuplicates(String ssn, String email, String phone, OnboardingRepository repository) {
        DuplicateType type = detectDuplicateType(ssn, email, phone, repository);
        return type != DuplicateType.NONE;
    }
}
