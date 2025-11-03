package com.abcbank.onboarding.adapter.in.web.dto;

/**
 * Response DTO for rate limit configuration information.
 */
public record RateLimitConfigResponse(
        EndpointRateLimitConfig createApplication,
        EndpointRateLimitConfig sendOtp,
        VerifyOtpRateLimitConfig verifyOtp,
        EndpointRateLimitConfig uploadDocument
) {
    public record EndpointRateLimitConfig(
            int maxRequests,
            int windowSeconds,
            String description
    ) {}

    public record VerifyOtpRateLimitConfig(
            ApplicationRateLimit application,
            IpAddressRateLimit ipAddress
    ) {
        public record ApplicationRateLimit(
                int maxRequests,
                int windowSeconds,
                String description
        ) {}

        public record IpAddressRateLimit(
                int maxRequests,
                int windowSeconds,
                String description
        ) {}
    }
}
