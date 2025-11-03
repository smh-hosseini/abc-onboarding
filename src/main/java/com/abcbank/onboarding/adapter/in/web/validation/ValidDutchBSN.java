package com.abcbank.onboarding.adapter.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a String is a valid Dutch BSN (Burgerservicenummer / Social Security Number).
 *
 * <p>BSN validation rules:
 * <ul>
 *   <li>Must be exactly 9 digits</li>
 *   <li>No leading zeros (BSN cannot start with 0)</li>
 *   <li>Must pass the 11-proof checksum algorithm (weighted sum modulo 11 must equal 0)</li>
 * </ul>
 *
 * <p>The 11-proof algorithm:
 * <pre>
 * Each digit is multiplied by its weight (9, 8, 7, 6, 5, 4, 3, 2, -1)
 * Sum of all products must be divisible by 11 (modulo 11 = 0)
 * Example: BSN 111222333
 * (1*9 + 1*8 + 1*7 + 2*6 + 2*5 + 2*4 + 3*3 + 3*2 + 3*-1) % 11 = 0
 * </pre>
 *
 * @see <a href="https://www.government.nl/topics/personal-data/citizen-service-number-bsn">BSN Information</a>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DutchBsnValidator.class)
@Documented
public @interface ValidDutchBSN {

    String message() default "Invalid Dutch BSN (Burgerservicenummer)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * If true, null values are considered valid.
     * Default is false (null values are invalid).
     */
    boolean allowNull() default false;
}
