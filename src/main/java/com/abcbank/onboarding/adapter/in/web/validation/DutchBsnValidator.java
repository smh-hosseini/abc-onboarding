package com.abcbank.onboarding.adapter.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for Dutch BSN (Burgerservicenummer).
 *
 * <p>Validates according to Dutch government standards:
 * <ul>
 *   <li>Exactly 9 digits</li>
 *   <li>No leading zeros</li>
 *   <li>11-proof checksum validation</li>
 * </ul>
 *
 * <p>Thread-safe and stateless.
 */
public class DutchBsnValidator implements ConstraintValidator<ValidDutchBSN, String> {

    private static final int BSN_LENGTH = 9;
    private static final int[] WEIGHTS = {9, 8, 7, 6, 5, 4, 3, 2, -1};
    private static final int MODULO = 11;

    private boolean allowNull;

    @Override
    public void initialize(ValidDutchBSN constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(String bsn, ConstraintValidatorContext context) {
        // Handle null values based on configuration
        if (bsn == null) {
            return allowNull;
        }

        // Remove any whitespace
        bsn = bsn.trim();

        // Check if empty after trimming
        if (bsn.isEmpty()) {
            return false;
        }

        // Check if exactly 9 characters
        if (bsn.length() != BSN_LENGTH) {
            addConstraintViolation(context, "BSN must be exactly 9 digits");
            return false;
        }

        // Check if all characters are digits
        if (!bsn.matches("\\d{9}")) {
            addConstraintViolation(context, "BSN must contain only digits");
            return false;
        }

        // Check for leading zero (BSN cannot start with 0)
        if (bsn.charAt(0) == '0') {
            addConstraintViolation(context, "BSN cannot start with 0");
            return false;
        }

        // Validate using 11-proof algorithm
        if (!validate11Proof(bsn)) {
            addConstraintViolation(context, "BSN failed 11-proof checksum validation");
            return false;
        }

        return true;
    }

    /**
     * Validates BSN using the 11-proof algorithm.
     *
     * <p>Algorithm:
     * <pre>
     * Each digit is multiplied by its weight: [9, 8, 7, 6, 5, 4, 3, 2, -1]
     * Sum of all products must be divisible by 11 (modulo 11 = 0)
     *
     * Example for BSN 111222333:
     * (1*9 + 1*8 + 1*7 + 2*6 + 2*5 + 2*4 + 3*3 + 3*2 + 3*-1) % 11
     * = (9 + 8 + 7 + 12 + 10 + 8 + 9 + 6 - 3) % 11
     * = 66 % 11
     * = 0 ✓ Valid
     * </pre>
     *
     * @param bsn the BSN to validate
     * @return true if BSN passes 11-proof, false otherwise
     */
    private boolean validate11Proof(String bsn) {
        int sum = 0;

        for (int i = 0; i < BSN_LENGTH; i++) {
            int digit = Character.getNumericValue(bsn.charAt(i));
            sum += digit * WEIGHTS[i];
        }

        return sum % MODULO == 0;
    }

    /**
     * Adds a custom constraint violation message to the context.
     *
     * @param context the validation context
     * @param message the custom message
     */
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
