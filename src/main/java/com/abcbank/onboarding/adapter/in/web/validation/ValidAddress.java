package com.abcbank.onboarding.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that an Address object contains valid Dutch address information.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Dutch postal code format: 1234 AB (4 digits + space + 2 uppercase letters)</li>
 *   <li>House number must be valid (digits, optionally followed by letter/suffix)</li>
 *   <li>Country code must be ISO 3166-1 alpha-2 format (2 uppercase letters)</li>
 *   <li>All required fields must be present and non-empty</li>
 * </ul>
 *
 * <p>Examples of valid Dutch postal codes:
 * <ul>
 *   <li>1234 AB</li>
 *   <li>1000 AA</li>
 *   <li>9999 ZZ</li>
 * </ul>
 *
 * <p>Examples of valid house numbers:
 * <ul>
 *   <li>123</li>
 *   <li>42A</li>
 *   <li>15-17</li>
 *   <li>100 bis</li>
 * </ul>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Postal_codes_in_the_Netherlands">Dutch Postal Codes</a>
 * @see <a href="https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2">ISO 3166-1 alpha-2</a>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AddressValidator.class)
@Documented
public @interface ValidAddress {

    String message() default "Invalid address format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * If true, null values are considered valid.
     * Default is false (null values are invalid).
     */
    boolean allowNull() default false;

    /**
     * If true, only validates Dutch addresses (NL country code).
     * If false, validates addresses for all countries but with basic validation.
     * Default is true (Dutch addresses only).
     */
    boolean dutchOnly() default true;
}
