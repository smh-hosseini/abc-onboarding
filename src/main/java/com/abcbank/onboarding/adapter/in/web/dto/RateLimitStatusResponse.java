package com.abcbank.onboarding.adapter.in.web.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for rate limit status information.
 */
public record RateLimitStatusResponse(
        String key,
        int currentCount,
        int limit,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        int remainingRequests
) {}
