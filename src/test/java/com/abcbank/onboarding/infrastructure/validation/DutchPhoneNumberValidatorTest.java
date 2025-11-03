package com.abcbank.onboarding.infrastructure.validation;

import com.abcbank.onboarding.adapter.in.web.validation.PhoneNumberValidator;
import com.abcbank.onboarding.adapter.in.web.validation.ValidPhoneNumber;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dutch Phone Number Validator Tests")
class DutchPhoneNumberValidatorTest {

    private PhoneNumberValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        validator = new PhoneNumberValidator();

        // Initialize validator with default annotation values
        ValidPhoneNumber annotation = new ValidPhoneNumber() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ValidPhoneNumber.class;
            }

            @Override
            public String message() {
                return "Invalid phone number";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public boolean allowNull() {
                return false;
            }

            @Override
            public String defaultRegion() {
                return "NL";
            }
        };

        validator.initialize(annotation);

        // Configure mock to handle constraint violation building
        lenient().doNothing().when(context).disableDefaultConstraintViolation();
        lenient().when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        lenient().when(builder.addConstraintViolation()).thenReturn(context);
    }

    @Test
    @DisplayName("Should reject null phone number by default")
    void shouldRejectNullPhoneNumber() {
        // By default allowNull is false
        assertThat(validator.isValid(null, context)).isFalse();
    }

    @Test
    @DisplayName("Should reject empty phone number")
    void shouldRejectEmptyPhoneNumber() {
        assertThat(validator.isValid("", context)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+31612345678",   // Mobile with country code
            "+31687654321",   // Mobile with country code
            "+31201234567",   // Landline with country code (Amsterdam)
            "+31101234567",   // Landline with country code (Rotterdam)
            "+31301234567"    // Landline with country code (Utrecht)
    })
    @DisplayName("Should accept valid Dutch phone numbers in E.164 format")
    void shouldAcceptValidDutchPhoneNumbers(String phoneNumber) {
        assertThat(validator.isValid(phoneNumber, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+32612345678",   // Belgian country code (not Dutch)
            "+1234567890",    // Invalid country code
            "0612345678",     // No + prefix (not E.164)
            "061234567",      // Too short
            "phone-number",   // Text
            "+316123"         // Too short
    })
    @DisplayName("Should reject invalid phone numbers")
    void shouldRejectInvalidPhoneNumbers(String phoneNumber) {
        assertThat(validator.isValid(phoneNumber, context)).isFalse();
    }

    @Test
    @DisplayName("Should accept phone number with double plus (libphonenumber handles it)")
    void shouldAcceptDoubleplus() {
        // libphonenumber library accepts "++31612345678" and parses it as valid
        assertThat(validator.isValid("++31612345678", context)).isTrue();
    }

    @Test
    @DisplayName("Should accept mobile numbers with E.164 format")
    void shouldAcceptMobileNumbersWithE164() {
        assertThat(validator.isValid("+31612345678", context)).isTrue();
    }

    @Test
    @DisplayName("Should reject mobile numbers without + prefix")
    void shouldRejectMobileNumbersWithoutPrefix() {
        assertThat(validator.isValid("0612345678", context)).isFalse();
    }

    @Test
    @DisplayName("Should accept phone number with letters (libphonenumber converts them)")
    void shouldAcceptPhoneNumberWithLetters() {
        // libphonenumber library converts letters to numbers (like old phone keypads)
        // "+31-6-ABCD-EFGH" becomes "+31622233344"
        assertThat(validator.isValid("+31-6-ABCD-EFGH", context)).isTrue();
    }

    @Test
    @DisplayName("Should reject phone number that is too short")
    void shouldRejectPhoneNumberThatIsTooShort() {
        assertThat(validator.isValid("+316123", context)).isFalse();
    }

    @Test
    @DisplayName("Should reject phone number that is too long")
    void shouldRejectPhoneNumberThatIsTooLong() {
        assertThat(validator.isValid("+316123456789123", context)).isFalse();
    }

    @Test
    @DisplayName("Should handle whitespace trimming")
    void shouldHandleWhitespaceTrimming() {
        assertThat(validator.isValid("  +31612345678  ", context)).isTrue();
    }

    @Test
    @DisplayName("Should reject non-Dutch number when default region is NL")
    void shouldRejectNonDutchNumber() {
        // Belgian number should be invalid if we're validating Dutch numbers
        assertThat(validator.isValid("+32612345678", context)).isFalse();
    }
}
