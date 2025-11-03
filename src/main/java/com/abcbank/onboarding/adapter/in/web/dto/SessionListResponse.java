package com.abcbank.onboarding.adapter.in.web.dto;

import java.util.List;

/**
 * Response DTO for listing user sessions.
 */
public record SessionListResponse(
        List<SessionInfoResponse> sessions,
        int totalSessions
) {}
