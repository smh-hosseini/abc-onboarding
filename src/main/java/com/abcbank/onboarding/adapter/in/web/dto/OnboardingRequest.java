package com.abcbank.onboarding.adapter.in.web.dto;

import com.abcbank.onboarding.domain.model.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Request DTO for creating a new onboarding application.
 * Contains all required personal information with comprehensive validation.
 */
@Schema(description = "Request to create a new onboarding application")
public record OnboardingRequest(
        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "First name must contain only letters, spaces, hyphens, and apostrophes")
        @Schema(description = "First name", example = "John")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "Last name must contain only letters, spaces, hyphens, and apostrophes")
        @Schema(description = "Last name", example = "Doe")
        String lastName,

        @NotNull(message = "Gender is required")
        @Schema(description = "Gender", example = "MALE", allowableValues = {"MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY"})
        Gender gender,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        @Schema(description = "Date of birth (must be at least 18 years old)", example = "1990-01-15")
        LocalDate dateOfBirth,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Phone number must be in E.164 format (e.g., +31612345678)")
        @Schema(description = "Phone number in E.164 format", example = "+31612345678")
        String phone,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Schema(description = "Email address", example = "john.doe@example.com")
        String email,

        @NotBlank(message = "Nationality is required")
        @Size(min = 2, max = 2, message = "Nationality code must be 2 characters (ISO 3166-1 alpha-2)")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Nationality code must be 2 uppercase letters")
        @Schema(description = "Nationality code (ISO 3166-1 alpha-2)", example = "NL")
        String nationality,

        @NotNull(message = "Residential address is required")
        @Valid
        @Schema(description = "Residential address")
        AddressRequest residentialAddress,

        @NotBlank(message = "Social Security Number is required")
        @Pattern(regexp = "^\\d{9}$", message = "Social Security Number must be exactly 9 digits")
        @Schema(description = "Social Security Number (9 digits)", example = "123456789")
        String socialSecurityNumber
) {
}
