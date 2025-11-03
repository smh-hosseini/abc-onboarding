package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for residential address information.
 */
@Schema(description = "Residential address information")
public record AddressResponse(
        @Schema(description = "Street name", example = "Main Street")
        String street,

        @Schema(description = "House number", example = "123A")
        String houseNumber,

        @Schema(description = "Postal code", example = "1234AB")
        String postalCode,

        @Schema(description = "City name", example = "Amsterdam")
        String city,

        @Schema(description = "Country code", example = "NL")
        String country
) {
}
