package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * Value object representing a residential address.
 * Immutable to ensure domain integrity.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class Address {
    private final String street;
    private final String houseNumber;
    private final String postalCode;
    private final String city;
    private final String country;

    public Address(String street, String houseNumber, String postalCode, String city, String country) {
        this.street = Objects.requireNonNull(street, "Street cannot be null");
        this.houseNumber = Objects.requireNonNull(houseNumber, "House number cannot be null");
        this.postalCode = Objects.requireNonNull(postalCode, "Postal code cannot be null");
        this.city = Objects.requireNonNull(city, "City cannot be null");
        this.country = Objects.requireNonNull(country, "Country cannot be null");
    }
}
