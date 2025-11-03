package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for messages.
 */
@Schema(description = "Generic API response")
public record ApiResponseDto(
        @Schema(description = "Success flag", example = "true")
        boolean success,

        @Schema(description = "Response message", example = "Operation completed successfully")
        String message,

        @Schema(description = "Response timestamp", example = "2025-01-15T10:30:00")
        LocalDateTime timestamp
) {
    public ApiResponseDto(boolean success, String message) {
        this(success, message, LocalDateTime.now());
    }

    public static ApiResponseDto success(String message) {
        return new ApiResponseDto(true, message);
    }

    public static ApiResponseDto error(String message) {
        return new ApiResponseDto(false, message);
    }
}
