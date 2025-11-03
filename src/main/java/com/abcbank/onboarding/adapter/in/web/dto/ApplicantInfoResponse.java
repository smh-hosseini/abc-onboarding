package com.abcbank.onboarding.adapter.in.web.dto;

import java.util.UUID;

/**
 * Response DTO for applicant information from /me endpoint.
 */
public record ApplicantInfoResponse(
        UUID applicationId,
        String type,
        String[] roles
) {
    public static ApplicantInfoResponse of(UUID applicationId) {
        return new ApplicantInfoResponse(applicationId, "applicant", new String[]{"APPLICANT"});
    }
}
