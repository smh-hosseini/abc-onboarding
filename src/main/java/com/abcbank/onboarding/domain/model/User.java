package com.abcbank.onboarding.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing an internal user (compliance officer or admin).
 * Used for username/password authentication.
 * Applicants are authenticated via OTP and don't have User entities.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    @EqualsAndHashCode.Include
    private UUID id;
    private String username;
    private String passwordHash; // BCrypt hashed password
    private String email;
    private String fullName;
    private UserRole role;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    /**
     * Constructor for creating a new user
     */
    public User(
            UUID id,
            String username,
            String passwordHash,
            String email,
            String fullName,
            UserRole role
    ) {
        this.id = Objects.requireNonNull(id, "User ID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.fullName = Objects.requireNonNull(fullName, "Full name cannot be null");
        this.role = Objects.requireNonNull(role, "Role cannot be null");

        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Updates the last login timestamp
     */
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /**
     * Deactivates the user account
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Activates the user account
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Updates the password hash
     */
    public void updatePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "Password hash cannot be null");
    }
}
