package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.ApiResponseDto;
import com.abcbank.onboarding.adapter.in.web.dto.RateLimitStatusResponse;
import com.abcbank.onboarding.adapter.in.web.dto.RateLimitConfigResponse;
import com.abcbank.onboarding.infrastructure.security.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Rate Limit Management Controller
 * Admin endpoints for viewing and managing rate limits
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/rate-limits")
@Tag(name = "Rate Limit Management", description = "Admin endpoints for rate limit operations")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class RateLimitController {

    private final RateLimitService rateLimitService;

    public RateLimitController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    /**
     * Get rate limit information for a specific key and resource
     */
    @GetMapping("/info")
    @Operation(summary = "Get rate limit info", description = "Admin: View rate limit status for a specific key")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rate limit info retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    public ResponseEntity<?> getRateLimitInfo(
            @RequestParam String key,
            @RequestParam String resource,
            @RequestParam int limit) {
        try {
            RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(key, resource, limit);

            RateLimitInfoResponse response = new RateLimitInfoResponse(
                    maskKey(key),
                    resource,
                    info.getLimit(),
                    (int) info.getCurrent(),
                    (int) info.getRemaining(),
                    info.getResetTimestamp()
            );

            log.info("Admin retrieved rate limit info for resource: {} key: {}", resource, maskKey(key));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving rate limit info", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Response record for rate limit info.
     */
    private record RateLimitInfoResponse(
            String key,
            String resource,
            int limit,
            int current,
            int remaining,
            long resetTimestamp
    ) {}

    /**
     * Reset rate limit for a specific key and resource
     */
    @DeleteMapping("/reset")
    @Operation(summary = "Reset rate limit", description = "Admin: Reset rate limit for a specific key and resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rate limit reset successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    public ResponseEntity<ApiResponseDto> resetRateLimit(
            @RequestParam String key,
            @RequestParam String resource) {
        try {
            rateLimitService.resetRateLimit(key, resource);

            log.info("Admin reset rate limit for resource: {} key: {}", resource, maskKey(key));
            return ResponseEntity.ok(ApiResponseDto.success(
                    "Rate limit reset successfully for resource: " + resource + " key: " + maskKey(key)
            ));
        } catch (Exception e) {
            log.error("Error resetting rate limit", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Reset all rate limits for a specific resource
     */
    @DeleteMapping("/reset-all")
    @Operation(summary = "Reset all rate limits", description = "Admin: Reset all rate limits for a resource")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All rate limits reset successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    public ResponseEntity<ApiResponseDto> resetAllRateLimits(@RequestParam String resource) {
        try {
            rateLimitService.resetAllRateLimits(resource);

            log.info("Admin reset all rate limits for resource: {}", resource);
            return ResponseEntity.ok(ApiResponseDto.success(
                    "All rate limits reset successfully for resource: " + resource
            ));
        } catch (Exception e) {
            log.error("Error resetting all rate limits", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Get rate limit configuration (from constants)
     */
    @GetMapping("/config")
    @Operation(summary = "Get rate limit configuration", description = "Admin: View current rate limit configurations")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rate limit configuration retrieved"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    public ResponseEntity<?> getRateLimitConfig() {
        try {
            // Create application rate limits
            RateLimitConfigResponse.EndpointRateLimitConfig createApplication =
                    new RateLimitConfigResponse.EndpointRateLimitConfig(
                            5,
                            3600, // 1 hour in seconds
                            "IP-based rate limit for creating applications"
                    );

            // Send OTP rate limits
            RateLimitConfigResponse.EndpointRateLimitConfig sendOtp =
                    new RateLimitConfigResponse.EndpointRateLimitConfig(
                            3,
                            3600, // 1 hour in seconds
                            "IP-based rate limit for sending OTP"
                    );

            // Verify OTP rate limits
            RateLimitConfigResponse.VerifyOtpRateLimitConfig.ApplicationRateLimit verifyOtpApp =
                    new RateLimitConfigResponse.VerifyOtpRateLimitConfig.ApplicationRateLimit(
                            3,
                            3600, // 1 hour in seconds
                            "Application-based rate limit for OTP verification"
                    );

            RateLimitConfigResponse.VerifyOtpRateLimitConfig.IpAddressRateLimit verifyOtpIp =
                    new RateLimitConfigResponse.VerifyOtpRateLimitConfig.IpAddressRateLimit(
                            10,
                            3600, // 1 hour in seconds
                            "IP-based rate limit for OTP verification"
                    );

            RateLimitConfigResponse.VerifyOtpRateLimitConfig verifyOtp =
                    new RateLimitConfigResponse.VerifyOtpRateLimitConfig(
                            verifyOtpApp,
                            verifyOtpIp
                    );

            // Upload document rate limits
            RateLimitConfigResponse.EndpointRateLimitConfig uploadDocument =
                    new RateLimitConfigResponse.EndpointRateLimitConfig(
                            10,
                            3600, // 1 hour in seconds
                            "Application-based rate limit for document uploads"
                    );

            RateLimitConfigResponse config = new RateLimitConfigResponse(
                    createApplication,
                    sendOtp,
                    verifyOtp,
                    uploadDocument
            );

            log.info("Admin retrieved rate limit configuration");
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error retrieving rate limit config", e);
            return ResponseEntity.status(500).body(ApiResponseDto.error("Internal server error"));
        }
    }

    /**
     * Mask key for GDPR compliance
     */
    private String maskKey(String key) {
        if (key == null || key.length() <= 4) {
            return "****";
        }
        return key.substring(0, 2) + "****" + key.substring(key.length() - 2);
    }
}
