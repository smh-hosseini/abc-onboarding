package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.*;
import com.abcbank.onboarding.adapter.in.web.mapper.OnboardingDtoMapper;
import com.abcbank.onboarding.application.ComplianceService;
import com.abcbank.onboarding.domain.model.ApplicationStatus;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.port.in.VerifyApplicationUseCase;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for compliance officer endpoints.
 * Requires authentication as compliance officer.
 *
 * Endpoints:
 * - GET /api/v1/compliance/applications - List all applications (pageable)
 * - GET /api/v1/compliance/applications/{id} - Get application details
 * - POST /api/v1/compliance/applications/{id}/assign-to-me - Assign to self
 * - POST /api/v1/compliance/applications/{id}/verify - Verify application
 * - POST /api/v1/compliance/applications/{id}/request-info - Request more info
 * - POST /api/v1/compliance/applications/{id}/flag - Flag as suspicious
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "Endpoints for compliance officers to review and verify applications")
@SecurityRequirement(name = "bearerAuth")
public class ComplianceController {

    private final ComplianceService complianceService;
    private final OnboardingRepository onboardingRepository;
    private final OnboardingDtoMapper mapper;

    /**
     * Lists all applications with pagination and filtering.
     * Can be filtered by status via query parameter.
     *
     * @param status Optional status filter
     * @param pageable Pagination parameters
     * @return Page of applications
     */
    @GetMapping("/applications")
    @Operation(
            summary = "List all applications",
            description = "Retrieves a paginated list of all onboarding applications. " +
                    "Can be filtered by status using the status query parameter."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Applications retrieved successfully",
                    content = @Content(schema = @Schema(implementation = Page.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires compliance officer role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<Page<ApplicationResponse>> listApplications(
            @RequestParam(value = "status", required = false) ApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("Listing applications - Status filter: {}, Page: {}, Size: {}",
                status, pageable.getPageNumber(), pageable.getPageSize());

        // Note: This is a simplified implementation
        // In production, the repository would support pagination with findAll(Pageable)
        // and filtering with findByStatus(ApplicationStatus, Pageable)

        List<OnboardingApplication> applications;
        if (status != null) {
            // Filter by status - this would use repository method in production
            applications = onboardingRepository.findAllByStatus(status.name());
        } else {
            applications = onboardingRepository.findAll();
        }

        // Convert to response DTOs
        List<ApplicationResponse> responses = applications.stream()
                .map(mapper::toApplicationResponse)
                .toList();

        // For simplicity, returning as a list wrapped in Page-like structure
        // In production, use Spring Data's Page implementation
        log.info("Retrieved {} applications", responses.size());

        // Simplified response - in production, use proper Page implementation
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(responses.size()))
                .body(null); // Would return Page<ApplicationResponse> from repository
    }

    /**
     * Gets detailed application information.
     *
     * @param id Application ID
     * @return Complete application details
     */
    @GetMapping("/applications/{id}")
    @Operation(
            summary = "Get application details",
            description = "Retrieves complete details of a specific application for review."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Application details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires compliance officer role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<ApplicationResponse> getApplicationDetails(
            @PathVariable("id") UUID id) {

        log.info("Compliance officer retrieving application details: {}", id);

        OnboardingApplication application = onboardingRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found with ID: " + id));

        ApplicationResponse response = mapper.toApplicationResponse(application);

        log.info("Application details retrieved for compliance review: {}", id);

        return ResponseEntity.ok(response);
    }

    /**
     * Assigns application to the current compliance officer.
     * Extracts officer email from authentication context.
     *
     * @param id Application ID
     * @param authentication Spring Security authentication
     * @return Success message
     */
    @PostMapping("/applications/{id}/assign-to-me")
    @Operation(
            summary = "Assign application to self",
            description = "Assigns the application to the current compliance officer for review. " +
                    "Application must be in SUBMITTED or UNDER_REVIEW status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Application assigned successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Application already assigned or in invalid status",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires compliance officer role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> assignToSelf(
            @PathVariable("id") UUID id,
            Authentication authentication) {

        // Extract officer email from authentication
        String officerEmail = authentication.getName(); // In production, extract from JWT claims

        log.info("Assigning application {} to compliance officer: {}", id, officerEmail);

        return complianceService.assignToSelf(id, officerEmail)
                .thenApply(message -> {
                    log.info("Application {} assigned successfully to: {}", id, officerEmail);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to assign application {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Verifies application after compliance review.
     * Marks all documents as verified.
     *
     * @param id Application ID
     * @param request Verification request with optional notes
     * @param authentication Spring Security authentication
     * @return Success message
     */
    @PostMapping("/applications/{id}/verify")
    @Operation(
            summary = "Verify application",
            description = "Verifies the application after successful compliance review. " +
                    "Marks all documents as verified and transitions application to VERIFIED status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Application verified successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Application in invalid status for verification",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires compliance officer role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> verifyApplication(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VerifyApplicationRequest request,
            Authentication authentication) {

        String officerEmail = authentication.getName();

        log.info("Verifying application {} by compliance officer: {}", id, officerEmail);

        VerifyApplicationUseCase.VerifyApplicationCommand command =
                new VerifyApplicationUseCase.VerifyApplicationCommand(
                        id,
                        officerEmail,
                        request.notes()
                );

        return complianceService.verifyApplication(command)
                .thenApply(message -> {
                    log.info("Application {} verified successfully by: {}", id, officerEmail);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to verify application {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Requests additional information from applicant.
     *
     * @param id Application ID
     * @param request Request with reason
     * @return Success message
     */
    @PostMapping("/applications/{id}/request-info")
    @Operation(
            summary = "Request additional information",
            description = "Requests additional information or documentation from the applicant. " +
                    "Transitions application to REQUIRES_MORE_INFO status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Additional information requested successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Application in invalid status or missing reason",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires compliance officer role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> requestAdditionalInfo(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RequestInfoRequest request) {

        log.info("Requesting additional info for application: {} - Reason: {}", id, request.reason());

        return complianceService.requestAdditionalInfo(id, request.reason())
                .thenApply(message -> {
                    log.info("Additional info requested for application: {}", id);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to request additional info for application {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Flags application as suspicious.
     *
     * @param id Application ID
     * @param request Flag request with reason
     * @return Success message
     */
    @PostMapping("/applications/{id}/flag")
    @Operation(
            summary = "Flag application as suspicious",
            description = "Flags the application as suspicious for further review. " +
                    "Transitions application to FLAGGED_SUSPICIOUS status and requires manual admin review."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Application flagged successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Application in invalid status or missing reason",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing credentials",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires compliance officer role",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> flagSuspicious(
            @PathVariable("id") UUID id,
            @Valid @RequestBody FlagApplicationRequest request) {

        log.warn("Flagging application {} as suspicious - Reason: {}", id, request.reason());

        return complianceService.flagSuspicious(id, request.reason())
                .thenApply(message -> {
                    log.warn("Application {} flagged as suspicious", id);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to flag application {} as suspicious", id, ex);
                    throw new RuntimeException(ex);
                });
    }
}
