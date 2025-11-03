package com.abcbank.onboarding.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a String is a valid phone number using Google's libphonenumber library.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Must be in E.164 format (international format starting with +)</li>
 *   <li>Must be a possible number (correct length for the region)</li>
 *   <li>Must be a valid number (passes additional validation checks)</li>
 *   <li>Supports Netherlands (+31) and international numbers</li>
 * </ul>
 *
 * <p>Examples of valid phone numbers:
 * <ul>
 *   <li>+31612345678 (Netherlands mobile)</li>
 *   <li>+31201234567 (Netherlands landline)</li>
 *   <li>+14155552671 (USA)</li>
 *   <li>+442071838750 (UK)</li>
 * </ul>
 *
 * @see <a href="https://github.com/google/libphonenumber">Google libphonenumber</a>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberValidator.class)
@Documented
public @interface ValidPhoneNumber {

    String message() default "Invalid phone number format. Use E.164 format (e.g., +31612345678)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * If true, null values are considered valid.
     * Default is false (null values are invalid).
     */
    boolean allowNull() default false;

    /**
     * The default region code to use if the number is not in international format.
     * Default is "NL" (Netherlands).
     *
     * <p>Note: It's recommended to always use E.164 international format
     * to avoid ambiguity.
     */
    String defaultRegion() default "NL";
}
