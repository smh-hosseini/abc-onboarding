package com.abcbank.onboarding.domain.port.in;

import com.abcbank.onboarding.domain.model.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Use case for creating a new onboarding application.
 * This is a driving port - called by adapters (controllers).
 */
public interface CreateApplicationUseCase {

    UUID execute(CreateApplicationCommand command);

    record CreateApplicationCommand(
            String firstName,
            String lastName,
            Gender gender,
            LocalDate dateOfBirth,
            String phone,
            String email,
            String nationality,
            Address residentialAddress,
            String socialSecurityNumber
    ) {}
}
