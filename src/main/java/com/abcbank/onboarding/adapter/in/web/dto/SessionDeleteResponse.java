package com.abcbank.onboarding.adapter.in.web.dto;

/**
 * Response DTO for session deletion operations.
 */
public record SessionDeleteResponse(
        String message,
        int deletedSessions
) {}
