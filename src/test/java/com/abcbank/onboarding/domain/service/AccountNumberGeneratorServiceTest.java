package com.abcbank.onboarding.domain.service;

import com.abcbank.onboarding.domain.port.out.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountNumberGeneratorService.
 * Tests IBAN generation, validation, and uniqueness logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountNumberGeneratorService Unit Tests")
class AccountNumberGeneratorServiceTest {

    private AccountNumberGeneratorService service;

    @Mock
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        service = new AccountNumberGeneratorService();
    }

    @Test
    @DisplayName("Should generate account number with correct format")
    void shouldGenerateAccountNumberWithCorrectFormat() {
        // When
        String accountNumber = service.generateAccountNumber();

        // Then
        assertThat(accountNumber).isNotNull();
        assertThat(accountNumber).hasSize(18); // NL (2) + check digits (2) + ABCB (4) + account (10)
        assertThat(accountNumber).startsWith("NL");
        assertThat(accountNumber).contains("ABCB");
    }

    @Test
    @DisplayName("Should generate account number with valid IBAN format")
    void shouldGenerateAccountNumberWithValidIbanFormat() {
        // When
        String accountNumber = service.generateAccountNumber();

        // Then
        assertThat(accountNumber).matches("NL\\d{2}ABCB\\d{10}");
    }

    @Test
    @DisplayName("Should generate different account numbers on multiple calls")
    void shouldGenerateDifferentAccountNumbers() {
        // When
        String accountNumber1 = service.generateAccountNumber();
        String accountNumber2 = service.generateAccountNumber();
        String accountNumber3 = service.generateAccountNumber();

        // Then
        assertThat(accountNumber1).isNotEqualTo(accountNumber2);
        assertThat(accountNumber2).isNotEqualTo(accountNumber3);
        assertThat(accountNumber1).isNotEqualTo(accountNumber3);
    }

    @Test
    @DisplayName("Should generate valid IBAN with correct check digits")
    void shouldGenerateValidIbanWithCorrectCheckDigits() {
        // When
        String accountNumber = service.generateAccountNumber();

        // Then - validate using the service's own validation method
        assertThat(service.validateIban(accountNumber)).isTrue();
    }

    @Test
    @DisplayName("Should validate correct IBAN")
    void shouldValidateCorrectIban() {
        // Given - Generate a valid IBAN from the service itself
        String validIban = service.generateAccountNumber();

        // When
        boolean isValid = service.validateIban(validIban);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject IBAN with invalid check digits")
    void shouldRejectIbanWithInvalidCheckDigits() {
        // Given - IBAN with intentionally wrong check digits
        String invalidIban = "NL00ABCB0417164300"; // Wrong check digits

        // When
        boolean isValid = service.validateIban(invalidIban);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject null IBAN")
    void shouldRejectNullIban() {
        // When
        boolean isValid = service.validateIban(null);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject too short IBAN")
    void shouldRejectTooShortIban() {
        // Given
        String shortIban = "NL9";

        // When
        boolean isValid = service.validateIban(shortIban);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject IBAN with invalid characters")
    void shouldRejectIbanWithInvalidCharacters() {
        // Given
        String invalidIban = "NL91ABCB04171643@#";

        // When
        boolean isValid = service.validateIban(invalidIban);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should ensure unique account number when no collision")
    void shouldEnsureUniqueAccountNumberWhenNoCollision() {
        // Given
        when(customerRepository.findByAccountNumber(anyString())).thenReturn(Optional.empty());

        // When
        String accountNumber = service.ensureUnique(customerRepository);

        // Then
        assertThat(accountNumber).isNotNull();
        assertThat(service.validateIban(accountNumber)).isTrue();
        verify(customerRepository, times(1)).findByAccountNumber(anyString());
    }

    @Test
    @DisplayName("Should retry and generate unique account number after collision")
    void shouldRetryAndGenerateUniqueAccountNumberAfterCollision() {
        // Given - First call returns collision, second call returns empty (unique)
        // CustomerRepository returns Optional<Customer>, so we need to mock a Customer object
        when(customerRepository.findByAccountNumber(anyString()))
                .thenReturn(Optional.of(mock(com.abcbank.onboarding.domain.model.Customer.class)))  // First attempt - collision
                .thenReturn(Optional.empty());                  // Second attempt - unique

        // When
        String accountNumber = service.ensureUnique(customerRepository);

        // Then
        assertThat(accountNumber).isNotNull();
        assertThat(service.validateIban(accountNumber)).isTrue();
        verify(customerRepository, times(2)).findByAccountNumber(anyString());
    }

    @Test
    @DisplayName("Should throw exception after max collision retries")
    void shouldThrowExceptionAfterMaxCollisionRetries() {
        // Given - Always return collision
        when(customerRepository.findByAccountNumber(anyString()))
                .thenReturn(Optional.of(mock(com.abcbank.onboarding.domain.model.Customer.class)));

        // When/Then
        assertThatThrownBy(() -> service.ensureUnique(customerRepository))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate unique account number after 10 attempts");

        verify(customerRepository, times(10)).findByAccountNumber(anyString());
    }

    @Test
    @DisplayName("Should throw exception when repository is null")
    void shouldThrowExceptionWhenRepositoryIsNull() {
        // When/Then
        assertThatThrownBy(() -> service.ensureUnique(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("CustomerRepository cannot be null");
    }

    @Test
    @DisplayName("Should return correct country code")
    void shouldReturnCorrectCountryCode() {
        // When
        String countryCode = service.getCountryCode();

        // Then
        assertThat(countryCode).isEqualTo("NL");
    }

    @Test
    @DisplayName("Should return correct bank code")
    void shouldReturnCorrectBankCode() {
        // When
        String bankCode = service.getBankCode();

        // Then
        assertThat(bankCode).isEqualTo("ABCB");
    }

    @Test
    @DisplayName("Should validate multiple generated IBANs")
    void shouldValidateMultipleGeneratedIbans() {
        // When - Generate multiple IBANs
        for (int i = 0; i < 100; i++) {
            String accountNumber = service.generateAccountNumber();

            // Then - All should be valid
            assertThat(service.validateIban(accountNumber))
                    .withFailMessage("Generated IBAN should be valid: " + accountNumber)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should generate account numbers with proper 10-digit padding")
    void shouldGenerateAccountNumbersWithProper10DigitPadding() {
        // When - Generate multiple account numbers
        for (int i = 0; i < 20; i++) {
            String accountNumber = service.generateAccountNumber();

            // Then - Extract the account part (last 10 digits)
            String accountPart = accountNumber.substring(8); // After NL##ABCB
            assertThat(accountPart).hasSize(10);
            assertThat(accountPart).matches("\\d{10}");
        }
    }

    @Test
    @DisplayName("Should validate IBAN case insensitively")
    void shouldValidateIbanCaseInsensitively() {
        // Given - Generate a valid IBAN and test both cases
        String upperCaseIban = service.generateAccountNumber();
        String lowerCaseIban = upperCaseIban.toLowerCase();

        // When/Then
        assertThat(service.validateIban(upperCaseIban)).isTrue();
        assertThat(service.validateIban(lowerCaseIban)).isTrue();
    }
}
