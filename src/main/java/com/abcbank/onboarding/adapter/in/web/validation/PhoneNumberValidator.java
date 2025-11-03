package com.abcbank.onboarding.adapter.in.web.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Validator implementation for phone numbers using Google's libphonenumber library.
 *
 * <p>Validates phone numbers according to international standards:
 * <ul>
 *   <li>E.164 format validation</li>
 *   <li>Number possibility check (correct length for region)</li>
 *   <li>Number validity check (comprehensive validation)</li>
 *   <li>Support for international numbers</li>
 * </ul>
 *
 * <p>Thread-safe and stateless.
 */
@Slf4j
public class PhoneNumberValidator implements ConstraintValidator<ValidPhoneNumber, String> {

    private static final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    private boolean allowNull;
    private String defaultRegion;

    @Override
    public void initialize(ValidPhoneNumber constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
        this.defaultRegion = constraintAnnotation.defaultRegion();
    }

    @Override
    public boolean isValid(String phoneNumber, ConstraintValidatorContext context) {
        // Handle null values based on configuration
        if (phoneNumber == null) {
            return allowNull;
        }

        // Remove any whitespace
        phoneNumber = phoneNumber.trim();

        // Check if empty after trimming
        if (phoneNumber.isEmpty()) {
            return false;
        }

        try {
            // Parse the phone number
            Phonenumber.PhoneNumber parsedNumber = parsePhoneNumber(phoneNumber);

            // Check if the number is possible (correct length for the region)
            if (!phoneNumberUtil.isPossibleNumber(parsedNumber)) {
                addConstraintViolation(context,
                    "Phone number is not a possible number for its region");
                return false;
            }

            // Check if the number is valid (comprehensive validation)
            if (!phoneNumberUtil.isValidNumber(parsedNumber)) {
                addConstraintViolation(context,
                    "Phone number failed validation checks");
                return false;
            }

            // Validate E.164 format (should start with +)
            if (!phoneNumber.startsWith("+")) {
                addConstraintViolation(context,
                    "Phone number must be in E.164 format (start with +)");
                return false;
            }

            return true;

        } catch (NumberParseException e) {
            log.debug("Failed to parse phone number: {}", phoneNumber, e);
            addConstraintViolation(context, getErrorMessage(e));
            return false;
        }
    }

    /**
     * Parses a phone number string into a PhoneNumber object.
     *
     * @param phoneNumber the phone number string to parse
     * @return parsed PhoneNumber object
     * @throws NumberParseException if the number cannot be parsed
     */
    private Phonenumber.PhoneNumber parsePhoneNumber(String phoneNumber) throws NumberParseException {
        // Try to parse with the default region
        // If the number starts with +, the region is ignored
        return phoneNumberUtil.parse(phoneNumber, defaultRegion);
    }

    /**
     * Converts a NumberParseException into a user-friendly error message.
     *
     * @param e the NumberParseException
     * @return user-friendly error message
     */
    private String getErrorMessage(NumberParseException e) {
        return switch (e.getErrorType()) {
            case INVALID_COUNTRY_CODE ->
                "Invalid country code. Phone number must start with a valid country code (e.g., +31 for Netherlands)";
            case NOT_A_NUMBER ->
                "Phone number contains invalid characters. Only digits, +, and spaces are allowed";
            case TOO_SHORT_NSN ->
                "Phone number is too short for the specified region";
            case TOO_LONG ->
                "Phone number is too long. Maximum length is 15 digits (E.164 standard)";
            case TOO_SHORT_AFTER_IDD ->
                "Phone number is too short after the international dialing prefix";
            default ->
                "Invalid phone number format. Use E.164 format (e.g., +31612345678)";
        };
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
