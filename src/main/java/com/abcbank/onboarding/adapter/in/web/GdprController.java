package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.ApiResponseDto;
import com.abcbank.onboarding.application.GdprComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for GDPR compliance endpoints.
 * Implements GDPR data subject rights.
 *
 * Endpoints:
 * - DELETE /api/v1/applicant/applications/{id}/delete-request - Request data deletion
 *
 * Note: Data export endpoint is in ApplicantController as it's part of normal applicant operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/applicant")
@RequiredArgsConstructor
@Tag(name = "GDPR", description = "GDPR compliance endpoints for data subject rights")
@SecurityRequirement(name = "bearerAuth")
public class GdprController {

    private final GdprComplianceService gdprService;

    /**
     * Requests deletion of personal data (GDPR Article 17 - Right to be Forgotten).
     * Can only be requested for REJECTED applications.
     *
     * @param id Application ID
     * @return Success message
     */
    @DeleteMapping("/applications/{id}/delete-request")
    @Operation(
            summary = "Request data deletion (GDPR)",
            description = "Requests deletion of all personal data associated with the application. " +
                    "Implements GDPR Article 17 - Right to be Forgotten. " +
                    "Can only be requested for rejected applications. " +
                    "Data will be anonymized after the legally required retention period."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "Data deletion request accepted",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - Application not in REJECTED status or already marked for deletion",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Cannot delete approved applications due to regulatory requirements",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> requestDataDeletion(
            @PathVariable("id") UUID id) {

        log.warn("Data deletion requested for application: {}", id);

        return gdprService.requestDeletion(id)
                .thenApply(message -> {
                    log.warn("Data deletion request accepted for application: {}", id);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to process data deletion request for application: {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

}
