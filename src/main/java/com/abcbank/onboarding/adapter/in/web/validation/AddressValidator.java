package com.abcbank.onboarding.adapter.in.web.validation;

import com.abcbank.onboarding.domain.model.Address;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator implementation for Dutch addresses.
 *
 * <p>Validates according to Dutch address standards:
 * <ul>
 *   <li>Postal code: 4 digits + space + 2 uppercase letters (e.g., "1234 AB")</li>
 *   <li>House number: digits, optionally followed by letters/suffix</li>
 *   <li>Country code: ISO 3166-1 alpha-2 (2 uppercase letters)</li>
 *   <li>All fields must be non-null and non-empty</li>
 * </ul>
 *
 * <p>Thread-safe and stateless.
 */
public class AddressValidator implements ConstraintValidator<ValidAddress, Address> {

    /**
     * Dutch postal code pattern: 4 digits + space + 2 uppercase letters.
     * Examples: "1234 AB", "1000 AA", "9999 ZZ"
     */
    private static final Pattern DUTCH_POSTAL_CODE_PATTERN = Pattern.compile("^\\d{4}\\s[A-Z]{2}$");

    /**
     * House number pattern: starts with digits, optionally followed by letters/symbols.
     * Examples: "123", "42A", "15-17", "100 bis"
     */
    private static final Pattern HOUSE_NUMBER_PATTERN = Pattern.compile("^\\d+[a-zA-Z0-9\\s\\-]*$");

    /**
     * ISO 3166-1 alpha-2 country code pattern: exactly 2 uppercase letters.
     * Examples: "NL", "BE", "DE", "US", "GB"
     */
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Z]{2}$");

    private static final String NETHERLANDS_CODE = "NL";

    private boolean allowNull;
    private boolean dutchOnly;

    @Override
    public void initialize(ValidAddress constraintAnnotation) {
        this.allowNull = constraintAnnotation.allowNull();
        this.dutchOnly = constraintAnnotation.dutchOnly();
    }

    @Override
    public boolean isValid(Address address, ConstraintValidatorContext context) {
        // Handle null values based on configuration
        if (address == null) {
            return allowNull;
        }

        // Disable default constraint violation to provide custom messages
        context.disableDefaultConstraintViolation();

        boolean isValid = true;

        // Validate street
        if (!isValidField(address.getStreet())) {
            addConstraintViolation(context, "street", "Street is required and cannot be empty");
            isValid = false;
        }

        // Validate house number
        if (!isValidField(address.getHouseNumber())) {
            addConstraintViolation(context, "houseNumber", "House number is required and cannot be empty");
            isValid = false;
        } else if (!HOUSE_NUMBER_PATTERN.matcher(address.getHouseNumber().trim()).matches()) {
            addConstraintViolation(context, "houseNumber",
                "House number must start with digits and may contain letters/symbols (e.g., '123', '42A', '15-17')");
            isValid = false;
        }

        // Validate city
        if (!isValidField(address.getCity())) {
            addConstraintViolation(context, "city", "City is required and cannot be empty");
            isValid = false;
        }

        // Validate country code
        if (!isValidField(address.getCountry())) {
            addConstraintViolation(context, "country", "Country code is required and cannot be empty");
            isValid = false;
        } else {
            String countryCode = address.getCountry().trim().toUpperCase();
            if (!COUNTRY_CODE_PATTERN.matcher(countryCode).matches()) {
                addConstraintViolation(context, "country",
                    "Country code must be ISO 3166-1 alpha-2 format (2 uppercase letters, e.g., 'NL', 'BE', 'DE')");
                isValid = false;
            } else if (dutchOnly && !NETHERLANDS_CODE.equals(countryCode)) {
                addConstraintViolation(context, "country",
                    "Only Dutch addresses (NL) are currently supported");
                isValid = false;
            }
        }

        // Validate postal code
        if (!isValidField(address.getPostalCode())) {
            addConstraintViolation(context, "postalCode", "Postal code is required and cannot be empty");
            isValid = false;
        } else {
            String postalCode = address.getPostalCode().trim();
            String countryCode = address.getCountry() != null ? address.getCountry().trim().toUpperCase() : "";

            // Apply Dutch postal code validation for NL addresses
            if (NETHERLANDS_CODE.equals(countryCode)) {
                if (!DUTCH_POSTAL_CODE_PATTERN.matcher(postalCode).matches()) {
                    addConstraintViolation(context, "postalCode",
                        "Dutch postal code must be in format '1234 AB' (4 digits + space + 2 uppercase letters)");
                    isValid = false;
                }
            } else if (dutchOnly) {
                // If dutchOnly is true, we already validated country above
                isValid = false;
            }
            // For non-Dutch addresses when dutchOnly=false, we skip postal code format validation
            // as formats vary widely between countries
        }

        return isValid;
    }

    /**
     * Checks if a field is valid (non-null and non-empty after trimming).
     *
     * @param field the field to check
     * @return true if field is valid, false otherwise
     */
    private boolean isValidField(String field) {
        return field != null && !field.trim().isEmpty();
    }

    /**
     * Adds a constraint violation with a property-specific path.
     *
     * @param context  the validation context
     * @param property the property name
     * @param message  the error message
     */
    private void addConstraintViolation(ConstraintValidatorContext context, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
               .addPropertyNode(property)
               .addConstraintViolation();
    }
}
