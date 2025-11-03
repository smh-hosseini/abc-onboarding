package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for username/password login.
 */
@Schema(description = "Login request with username and password")
public record LoginRequest(
        @Schema(description = "Username (email)", example = "officer@abc.nl", required = true)
        @NotBlank(message = "Username is required")
        String username,

        @Schema(description = "Password", example = "Officer123!", required = true)
        @NotBlank(message = "Password is required")
        String password
) {}
