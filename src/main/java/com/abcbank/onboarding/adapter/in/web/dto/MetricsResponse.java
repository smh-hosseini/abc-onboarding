package com.abcbank.onboarding.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response DTO for application metrics and statistics.
 */
@Schema(description = "Application metrics and statistics")
public record MetricsResponse(
        @Schema(description = "Total number of applications", example = "150")
        long total,

        @Schema(description = "Number of approved applications", example = "75")
        long approved,

        @Schema(description = "Number of rejected applications", example = "20")
        long rejected,

        @Schema(description = "Number of pending applications (all non-terminal states)", example = "55")
        long pending,

        @Schema(description = "Number of submitted applications", example = "10")
        long submitted,

        @Schema(description = "Number of applications under review", example = "15")
        long underReview,

        @Schema(description = "Number of verified applications", example = "5")
        long verified,

        @Schema(description = "Number of flagged applications", example = "3")
        long flagged,

        @Schema(description = "Number of applications requiring more info", example = "2")
        long requiresMoreInfo,

        @Schema(description = "Average processing time in hours", example = "24.5")
        double averageProcessingTimeHours,

        @Schema(description = "Timestamp when metrics were generated", example = "2025-01-15T15:00:00")
        LocalDateTime generatedAt
) {
}
