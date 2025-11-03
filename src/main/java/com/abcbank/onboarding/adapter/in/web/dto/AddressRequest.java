package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for residential address information.
 */
@Schema(description = "Residential address information")
public record AddressRequest(
        @NotBlank(message = "Street name is required")
        @Size(min = 2, max = 100, message = "Street name must be between 2 and 100 characters")
        @Schema(description = "Street name", example = "Main Street")
        String street,

        @NotBlank(message = "House number is required")
        @Size(min = 1, max = 10, message = "House number must be between 1 and 10 characters")
        @Schema(description = "House number", example = "123A")
        String houseNumber,

        @NotBlank(message = "Postal code is required")
        @Pattern(regexp = "^[A-Z0-9]{4,10}$", message = "Postal code must be 4-10 alphanumeric characters")
        @Schema(description = "Postal code", example = "1234AB")
        String postalCode,

        @NotBlank(message = "City is required")
        @Size(min = 2, max = 50, message = "City name must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z\\s-]+$", message = "City name must contain only letters, spaces, and hyphens")
        @Schema(description = "City name", example = "Amsterdam")
        String city,

        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 2, message = "Country code must be 2 characters (ISO 3166-1 alpha-2)")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be 2 uppercase letters")
        @Schema(description = "Country code (ISO 3166-1 alpha-2)", example = "NL")
        String country
) {
}
