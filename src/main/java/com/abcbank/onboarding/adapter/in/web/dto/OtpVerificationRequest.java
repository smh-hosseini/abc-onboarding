package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for OTP verification.
 */
@Schema(description = "Request to verify OTP code")
public record OtpVerificationRequest(
        @NotBlank(message = "OTP code is required")
        @Size(min = 6, max = 6, message = "OTP code must be exactly 6 characters")
        @Pattern(regexp = "^\\d{6}$", message = "OTP code must be 6 digits")
        @Schema(description = "6-digit OTP code", example = "123456")
        String otp,

        @NotNull(message = "Channel is required")
        @Schema(description = "Channel used for OTP", example = "EMAIL", allowableValues = {"EMAIL", "SMS"})
        SendOtpRequest.OtpChannel channel
) {
}
