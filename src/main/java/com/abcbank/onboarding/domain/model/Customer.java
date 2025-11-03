package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing an approved customer.
 * Created only after application approval.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Customer {
    @EqualsAndHashCode.Include
    private UUID id;
    private String customerId; // Format: CUST-YYYYMMDD-###
    private String accountNumber; // Format: NL##ABCB##########
    private UUID onboardingApplicationId;

    // Personal Information (encrypted at persistence layer)
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    private boolean active;
    private LocalDateTime createdAt;

    public Customer(
            UUID id,
            String customerId,
            String accountNumber,
            UUID onboardingApplicationId,
            String firstName,
            String lastName,
            String email,
            String phone
    ) {
        this.id = Objects.requireNonNull(id, "Customer ID cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID string cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.onboardingApplicationId = Objects.requireNonNull(onboardingApplicationId,
                "Onboarding application ID cannot be null");
        this.firstName = Objects.requireNonNull(firstName, "First name cannot be null");
        this.lastName = Objects.requireNonNull(lastName, "Last name cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.phone = Objects.requireNonNull(phone, "Phone cannot be null");

        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
