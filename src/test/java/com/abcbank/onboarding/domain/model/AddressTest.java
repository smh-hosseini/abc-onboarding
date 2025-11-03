package com.abcbank.onboarding.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Address value object.
 * Tests immutability, validation, and equality.
 */
@DisplayName("Address Unit Tests")
class AddressTest {

    @Test
    @DisplayName("Should create address with all fields")
    void shouldCreateAddressWithAllFields() {
        // When
        Address address = new Address(
                "Main Street",
                "10",
                "1011AB",
                "Amsterdam",
                "NL"
        );

        // Then
        assertThat(address).isNotNull();
        assertThat(address.getStreet()).isEqualTo("Main Street");
        assertThat(address.getHouseNumber()).isEqualTo("10");
        assertThat(address.getPostalCode()).isEqualTo("1011AB");
        assertThat(address.getCity()).isEqualTo("Amsterdam");
        assertThat(address.getCountry()).isEqualTo("NL");
    }

    @Test
    @DisplayName("Should throw exception when street is null")
    void shouldThrowExceptionWhenStreetIsNull() {
        // When/Then
        assertThatThrownBy(() -> new Address(
                null,
                "10",
                "1011AB",
                "Amsterdam",
                "NL"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Street cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when house number is null")
    void shouldThrowExceptionWhenHouseNumberIsNull() {
        // When/Then
        assertThatThrownBy(() -> new Address(
                "Main Street",
                null,
                "1011AB",
                "Amsterdam",
                "NL"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("House number cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when postal code is null")
    void shouldThrowExceptionWhenPostalCodeIsNull() {
        // When/Then
        assertThatThrownBy(() -> new Address(
                "Main Street",
                "10",
                null,
                "Amsterdam",
                "NL"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Postal code cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when city is null")
    void shouldThrowExceptionWhenCityIsNull() {
        // When/Then
        assertThatThrownBy(() -> new Address(
                "Main Street",
                "10",
                "1011AB",
                null,
                "NL"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("City cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when country is null")
    void shouldThrowExceptionWhenCountryIsNull() {
        // When/Then
        assertThatThrownBy(() -> new Address(
                "Main Street",
                "10",
                "1011AB",
                "Amsterdam",
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Country cannot be null");
    }

    @Test
    @DisplayName("Should be equal when all fields match")
    void shouldBeEqualWhenAllFieldsMatch() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        // When/Then
        assertThat(address1).isEqualTo(address2);
        assertThat(address1.hashCode()).isEqualTo(address2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when fields differ")
    void shouldNotBeEqualWhenFieldsDiffer() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Second Street", "10", "1011AB", "Amsterdam", "NL");

        // When/Then
        assertThat(address1).isNotEqualTo(address2);
    }

    @Test
    @DisplayName("Should not be equal when house number differs")
    void shouldNotBeEqualWhenHouseNumberDiffers() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Main Street", "20", "1011AB", "Amsterdam", "NL");

        // When/Then
        assertThat(address1).isNotEqualTo(address2);
    }

    @Test
    @DisplayName("Should not be equal when postal code differs")
    void shouldNotBeEqualWhenPostalCodeDiffers() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Main Street", "10", "2000XY", "Amsterdam", "NL");

        // When/Then
        assertThat(address1).isNotEqualTo(address2);
    }

    @Test
    @DisplayName("Should not be equal when city differs")
    void shouldNotBeEqualWhenCityDiffers() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Main Street", "10", "1011AB", "Rotterdam", "NL");

        // When/Then
        assertThat(address1).isNotEqualTo(address2);
    }

    @Test
    @DisplayName("Should not be equal when country differs")
    void shouldNotBeEqualWhenCountryDiffers() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Main Street", "10", "1011AB", "Amsterdam", "BE");

        // When/Then
        assertThat(address1).isNotEqualTo(address2);
    }

    @Test
    @DisplayName("Should not be equal to null")
    void shouldNotBeEqualToNull() {
        // Given
        Address address = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        // When/Then
        assertThat(address).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Should not be equal to different type")
    void shouldNotBeEqualToDifferentType() {
        // Given
        Address address = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        String notAnAddress = "Main Street 10";

        // When/Then
        assertThat(address).isNotEqualTo(notAnAddress);
    }

    @Test
    @DisplayName("Should have consistent hashCode for equal objects")
    void shouldHaveConsistentHashCodeForEqualObjects() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        // When/Then
        assertThat(address1.hashCode()).isEqualTo(address2.hashCode());
    }

    @Test
    @DisplayName("Should have different hashCode for different objects")
    void shouldHaveDifferentHashCodeForDifferentObjects() {
        // Given
        Address address1 = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");
        Address address2 = new Address("Second Street", "20", "2000XY", "Rotterdam", "BE");

        // When/Then
        assertThat(address1.hashCode()).isNotEqualTo(address2.hashCode());
    }

    @Test
    @DisplayName("Should have readable toString representation")
    void shouldHaveReadableToStringRepresentation() {
        // Given
        Address address = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        // When
        String toString = address.toString();

        // Then
        assertThat(toString).isNotNull();
        assertThat(toString).contains("Main Street");
        assertThat(toString).contains("10");
        assertThat(toString).contains("1011AB");
        assertThat(toString).contains("Amsterdam");
        assertThat(toString).contains("NL");
    }

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
        // Given
        Address address = new Address("Main Street", "10", "1011AB", "Amsterdam", "NL");

        // When/Then
        assertThat(address).isEqualTo(address);
    }

    @Test
    @DisplayName("Should support Dutch postal code format")
    void shouldSupportDutchPostalCodeFormat() {
        // Given
        Address address = new Address("Main Street", "10", "1011 AB", "Amsterdam", "NL");

        // Then
        assertThat(address.getPostalCode()).isEqualTo("1011 AB");
    }

    @Test
    @DisplayName("Should support house number with additions")
    void shouldSupportHouseNumberWithAdditions() {
        // Given
        Address address = new Address("Main Street", "10A", "1011AB", "Amsterdam", "NL");

        // Then
        assertThat(address.getHouseNumber()).isEqualTo("10A");
    }

    @Test
    @DisplayName("Should support international addresses")
    void shouldSupportInternationalAddresses() {
        // When
        Address usAddress = new Address("5th Avenue", "123", "10001", "New York", "US");
        Address ukAddress = new Address("Baker Street", "221B", "NW1 6XE", "London", "UK");
        Address deAddress = new Address("Hauptstraße", "42", "10115", "Berlin", "DE");

        // Then
        assertThat(usAddress.getCountry()).isEqualTo("US");
        assertThat(ukAddress.getHouseNumber()).isEqualTo("221B");
        assertThat(deAddress.getStreet()).isEqualTo("Hauptstraße");
    }
}
