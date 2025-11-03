package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.*;
import com.abcbank.onboarding.application.AdminService;
import com.abcbank.onboarding.application.GdprComplianceService;
import com.abcbank.onboarding.domain.port.in.ApproveApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.RejectApplicationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for administrator endpoints.
 * Requires authentication as administrator.
 *
 * Endpoints:
 * - POST /api/v1/admin/applications/{id}/approve - Approve application
 * - POST /api/v1/admin/applications/{id}/reject - Reject application
 * - GET /api/v1/admin/metrics - Get application metrics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Endpoints for administrators to approve/reject applications and view metrics")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final GdprComplianceService gdprService;

    /**
     * Approves an application and creates customer account.
     * Application must be in VERIFIED or FLAGGED_SUSPICIOUS status.
     *
     * @param id Application ID
     * @param request Approval request with optional notes
     * @param authentication Spring Security authentication
     * @return Account number
     */
    @PostMapping("/applications/{id}/approve")
    @Operation(
            summary = "Approve application",
            description = "Approves a verified application and creates customer account. " +
                    "Generates unique account number and transitions application to APPROVED status. " +
                    "Sends approval notification to applicant."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Application approved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Application in invalid status for approval",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires administrator role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> approveApplication(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) ApprovalRequest request,
            Authentication authentication) {

        String adminEmail = authentication.getName();

        log.info("Admin {} approving application: {}", adminEmail, id);

        ApproveApplicationUseCase.ApproveApplicationCommand command =
                new ApproveApplicationUseCase.ApproveApplicationCommand(
                        id,
                        adminEmail,
                        request != null ? request.approvalNotes() : null
                );

        return adminService.approveApplication(command)
                .thenApply(accountNumber -> {
                    log.info("Application {} approved successfully by admin: {}. Account number: {}",
                            id, adminEmail, accountNumber);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(
                                    "Application approved successfully. Account number: " + accountNumber
                            ));
                })
                .exceptionally(ex -> {
                    log.error("Failed to approve application {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Rejects an application with a reason.
     * Application can be rejected from UNDER_REVIEW, VERIFIED, or FLAGGED_SUSPICIOUS status.
     *
     * @param id Application ID
     * @param request Rejection request with reason
     * @param authentication Spring Security authentication
     * @return Success message
     */
    @PostMapping("/applications/{id}/reject")
    @Operation(
            summary = "Reject application",
            description = "Rejects an application with a mandatory reason. " +
                    "Transitions application to REJECTED status and sends rejection notification to applicant."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Application rejected successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or missing rejection reason",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires administrator role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> rejectApplication(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectionRequest request,
            Authentication authentication) {

        String adminEmail = authentication.getName();

        log.info("Admin {} rejecting application: {} - Reason: {}", adminEmail, id, request.rejectionReason());

        RejectApplicationUseCase.RejectApplicationCommand command =
                new RejectApplicationUseCase.RejectApplicationCommand(
                        id,
                        adminEmail,
                        request.rejectionReason()
                );

        return adminService.rejectApplication(command)
                .thenApply(message -> {
                    log.info("Application {} rejected successfully by admin: {}", id, adminEmail);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to reject application {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Gets application metrics and statistics.
     * Provides dashboard data for administrators.
     *
     * @return Metrics response with counts and statistics
     */
    @GetMapping("/metrics")
    @Operation(
            summary = "Get application metrics",
            description = "Retrieves comprehensive metrics about all applications including " +
                    "counts by status, average processing time, and other statistics for admin dashboard."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Metrics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MetricsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires administrator role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<MetricsResponse>> getMetrics() {

        log.info("Retrieving application metrics");

        return adminService.getApplicationMetrics()
                .thenApply(metricsMap -> {
                    // Convert Map to MetricsResponse DTO
                    MetricsResponse response = new MetricsResponse(
                            ((Number) metricsMap.getOrDefault("total", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("approved", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("rejected", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("pending", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("submitted", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("underReview", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("verified", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("flagged", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("requiresMoreInfo", 0)).longValue(),
                            ((Number) metricsMap.getOrDefault("averageProcessingTime", 0.0)).doubleValue(),
                            LocalDateTime.now()
                    );

                    log.info("Metrics retrieved successfully - Total applications: {}", response.total());

                    return ResponseEntity.ok(response);
                })
                .exceptionally(ex -> {
                    log.error("Failed to retrieve metrics", ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Processes data deletion (anonymization) for GDPR compliance.
     * This endpoint anonymizes personal data after the retention period has passed.
     * Can only be called for applications marked for deletion.
     *
     * @param id Application ID
     * @return Success message
     */
    @PostMapping("/applications/{id}/process-deletion")
    @Operation(
            summary = "Process data deletion (GDPR)",
            description = "Processes data deletion by anonymizing personal information. " +
                    "This endpoint should be called by scheduled jobs after the retention period. " +
                    "Implements GDPR Right to be Forgotten by anonymizing all PII data."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Data anonymized successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - Application not marked for deletion or retention period not passed",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires administrator role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> processDataDeletion(
            @PathVariable("id") UUID id) {

        log.warn("Admin processing data deletion (anonymization) for application: {}", id);

        return gdprService.processDataDeletion(id)
                .thenApply(message -> {
                    log.warn("Data anonymized successfully for application: {}", id);
                    return ResponseEntity.ok(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to process data deletion for application: {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }
}
