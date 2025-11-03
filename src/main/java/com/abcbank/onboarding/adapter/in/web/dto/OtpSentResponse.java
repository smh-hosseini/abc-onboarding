package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for OTP sent operation.
 */
@Schema(description = "Response after sending OTP")
public record OtpSentResponse(
        @Schema(description = "Success message", example = "OTP sent successfully to your email")
        String message,

        @Schema(description = "OTP expiration time in seconds", example = "600")
        int expiresIn
) {
}
