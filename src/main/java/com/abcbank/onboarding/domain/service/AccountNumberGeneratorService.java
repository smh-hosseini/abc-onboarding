package com.abcbank.onboarding.domain.service;

import com.abcbank.onboarding.domain.port.out.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Domain service for generating unique bank account numbers.
 * Generates Dutch IBAN-compliant account numbers with proper checksum validation.
 *
 * IBAN Format: NL##ABCB##########
 * - NL: Country code (Netherlands)
 * - ##: Check digits (calculated using mod-97)
 * - ABCB: Bank code (ABC Bank)
 * - ##########: 10-digit account number
 *
 * Standards Compliance:
 * - ISO 13616: International Bank Account Number (IBAN) standard
 * - ECBS EBS204: IBAN Registry
 * - Mod-97 checksum algorithm as per ISO 7064
 *
 * Thread Safety: This service uses SecureRandom which is thread-safe.
 */
@Slf4j
@Service
public class AccountNumberGeneratorService {

    private static final String COUNTRY_CODE = "NL";
    private static final String BANK_CODE = "ABCB";
    private static final int ACCOUNT_NUMBER_LENGTH = 10;
    private static final int MAX_COLLISION_RETRIES = 10;

    private final SecureRandom secureRandom;

    /**
     * Default constructor initializing SecureRandom.
     */
    public AccountNumberGeneratorService() {
        this.secureRandom = new SecureRandom();
        log.info("AccountNumberGeneratorService initialized with bank code: {}", BANK_CODE);
    }

    /**
     * Generates a complete IBAN-compliant Dutch account number.
     * Format: NL##ABCB##########
     *
     * The check digits are calculated using the mod-97 algorithm to ensure
     * the IBAN is valid and can be verified by banking systems.
     *
     * @return A valid Dutch IBAN account number
     */
    public String generateAccountNumber() {
        String accountNumber = generateRandomAccountNumber();
        String checkDigits = calculateCheckDigits(accountNumber);
        String iban = COUNTRY_CODE + checkDigits + BANK_CODE + accountNumber;

        log.debug("Generated IBAN: {} (masked: {}***)", iban.substring(0, 8), iban.substring(8, 12));

        return iban;
    }

    /**
     * Generates a unique account number ensuring no collision with existing accounts.
     * Retries up to MAX_COLLISION_RETRIES times if a collision is detected.
     *
     * @param customerRepository the repository to check for existing account numbers
     * @return A unique IBAN account number
     * @throws IllegalStateException if unable to generate a unique number after max retries
     */
    public String ensureUnique(CustomerRepository customerRepository) {
        Objects.requireNonNull(customerRepository, "CustomerRepository cannot be null");

        int attempts = 0;
        String accountNumber;

        do {
            accountNumber = generateAccountNumber();
            attempts++;

            if (customerRepository.findByAccountNumber(accountNumber).isEmpty()) {
                log.info("Generated unique account number after {} attempt(s)", attempts);
                return accountNumber;
            }

            log.warn("Account number collision detected (attempt {}): {}", attempts, maskAccountNumber(accountNumber));

            if (attempts >= MAX_COLLISION_RETRIES) {
                log.error("Failed to generate unique account number after {} attempts", MAX_COLLISION_RETRIES);
                throw new IllegalStateException(
                        "Unable to generate unique account number after " + MAX_COLLISION_RETRIES + " attempts"
                );
            }

        } while (attempts < MAX_COLLISION_RETRIES);

        // This should never be reached due to the exception above, but for completeness
        throw new IllegalStateException("Unexpected error in account number generation");
    }

    /**
     * Generates a random 10-digit account number.
     * Uses SecureRandom to ensure cryptographic randomness.
     *
     * @return A 10-digit account number as a String
     */
    private String generateRandomAccountNumber() {
        long randomNumber = Math.abs(secureRandom.nextLong()) % 10_000_000_000L;
        return String.format("%010d", randomNumber);
    }

    /**
     * Calculates the IBAN check digits using the mod-97 algorithm.
     *
     * Algorithm:
     * 1. Construct: BANK_CODE + ACCOUNT_NUMBER + COUNTRY_CODE + "00"
     * 2. Replace letters with numbers: A=10, B=11, ..., Z=35
     * 3. Calculate: 98 - (constructed_number mod 97)
     * 4. Format as 2-digit string with leading zero if necessary
     *
     * @param accountNumber the 10-digit account number
     * @return 2-digit check digits as a String
     */
    private String calculateCheckDigits(String accountNumber) {
        // Step 1: Construct the base string for calculation
        // Format: BANK_CODE + ACCOUNT_NUMBER + COUNTRY_CODE + "00"
        String base = BANK_CODE + accountNumber + COUNTRY_CODE + "00";

        // Step 2: Convert letters to numbers (A=10, B=11, etc.)
        StringBuilder numericString = new StringBuilder();
        for (char c : base.toCharArray()) {
            if (Character.isLetter(c)) {
                // Convert letter to number: A=10, B=11, ..., Z=35
                int numericValue = Character.toUpperCase(c) - 'A' + 10;
                numericString.append(numericValue);
            } else {
                numericString.append(c);
            }
        }

        // Step 3: Calculate mod 97
        BigInteger numericValue = new BigInteger(numericString.toString());
        BigInteger remainder = numericValue.mod(BigInteger.valueOf(97));

        // Step 4: Calculate check digits: 98 - remainder
        int checkDigits = 98 - remainder.intValue();

        // Format as 2-digit string with leading zero if necessary
        return String.format("%02d", checkDigits);
    }

    /**
     * Validates whether an IBAN account number has correct check digits.
     * Useful for validating account numbers received from external systems.
     *
     * @param iban the IBAN to validate
     * @return true if the IBAN check digits are valid, false otherwise
     */
    public boolean validateIban(String iban) {
        if (iban == null || iban.length() < 4) {
            return false;
        }

        try {
            // Rearrange: Move first 4 characters to the end
            String rearranged = iban.substring(4) + iban.substring(0, 4);

            // Convert letters to numbers
            StringBuilder numericString = new StringBuilder();
            for (char c : rearranged.toCharArray()) {
                if (Character.isLetter(c)) {
                    int numericValue = Character.toUpperCase(c) - 'A' + 10;
                    numericString.append(numericValue);
                } else if (Character.isDigit(c)) {
                    numericString.append(c);
                } else {
                    return false; // Invalid character
                }
            }

            // Check if mod 97 equals 1
            BigInteger numericValue = new BigInteger(numericString.toString());
            boolean valid = numericValue.mod(BigInteger.valueOf(97)).intValue() == 1;

            if (valid) {
                log.debug("IBAN validation successful: {}", maskAccountNumber(iban));
            } else {
                log.warn("IBAN validation failed: {}", maskAccountNumber(iban));
            }

            return valid;

        } catch (Exception e) {
            log.error("Error validating IBAN: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Masks an account number for secure logging.
     * Shows only the first 8 characters (country, check digits, and bank code).
     *
     * @param accountNumber the account number to mask
     * @return masked account number
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 8) {
            return "****";
        }
        return accountNumber.substring(0, 8) + "**********";
    }

    /**
     * Gets the country code used for account generation.
     *
     * @return the country code (NL)
     */
    public String getCountryCode() {
        return COUNTRY_CODE;
    }

    /**
     * Gets the bank code used for account generation.
     *
     * @return the bank code (ABCB)
     */
    public String getBankCode() {
        return BANK_CODE;
    }
}
