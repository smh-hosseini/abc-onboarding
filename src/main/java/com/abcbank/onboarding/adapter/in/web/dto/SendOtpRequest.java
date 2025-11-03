package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for sending OTP with specified channel.
 */
@Schema(description = "Request for sending OTP to a specific channel")
public record SendOtpRequest(
        @NotNull(message = "Channel is required")
        @Schema(description = "Channel to send OTP through", example = "EMAIL", allowableValues = {"EMAIL", "SMS"})
        OtpChannel channel
) {
    public enum OtpChannel {
        EMAIL,
        SMS
    }
}
