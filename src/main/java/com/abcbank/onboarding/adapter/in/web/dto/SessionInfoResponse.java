package com.abcbank.onboarding.adapter.in.web.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for session information.
 */
public record SessionInfoResponse(
        String sessionId,
        String username,
        LocalDateTime createdAt,
        LocalDateTime lastAccessedAt,
        LocalDateTime expiresAt,
        String ipAddress,
        String userAgent
) {}
