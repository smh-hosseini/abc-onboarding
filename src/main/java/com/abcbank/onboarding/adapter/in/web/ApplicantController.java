package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.*;
import com.abcbank.onboarding.adapter.in.web.mapper.OnboardingDtoMapper;
import com.abcbank.onboarding.application.GdprComplianceService;
import com.abcbank.onboarding.application.OnboardingApplicationService;
import com.abcbank.onboarding.domain.model.ApplicationDocument;
import com.abcbank.onboarding.domain.model.DocumentType;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.port.in.ExportPersonalDataUseCase;
import com.abcbank.onboarding.domain.port.in.ProvideAdditionalInfoUseCase;
import com.abcbank.onboarding.domain.port.in.SubmitApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.UploadDocumentUseCase;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import com.abcbank.onboarding.infrastructure.security.JwtAuthenticationDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for authenticated applicant endpoints.
 * Requires JWT token from OTP verification.
 *
 * Endpoints:
 * - GET /api/v1/applicant/applications/{id} - Get application details
 * - POST /api/v1/applicant/applications/{id}/documents - Upload document
 * - POST /api/v1/applicant/applications/{id}/submit - Submit for review
 * - POST /api/v1/applicant/applications/{id}/provide-info - Provide additional information
 * - GET /api/v1/applicant/applications/{id}/export - GDPR data export
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/applicant")
@RequiredArgsConstructor
@Tag(name = "Applicant", description = "Authenticated endpoints for applicants to manage their applications")
@SecurityRequirement(name = "bearerAuth")
public class ApplicantController {

    private final OnboardingApplicationService onboardingService;
    private final GdprComplianceService gdprService;
    private final OnboardingRepository onboardingRepository;
    private final OnboardingDtoMapper mapper;

    /**
     * Validates that the authenticated applicant has permission to access the requested application.
     * Prevents applicants from accessing other applicants' applications.
     *
     * @param requestedApplicationId The application ID being accessed
     * @throws AccessDeniedException if the applicant is not authorized to access the application
     */
    private void validateApplicantAuthorization(UUID requestedApplicationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getDetails() == null) {
            log.warn("No authentication details found for authorization check");
            throw new AccessDeniedException("Authentication required");
        }

        if (!(authentication.getDetails() instanceof JwtAuthenticationDetails)) {
            log.warn("Invalid authentication details type: {}", authentication.getDetails().getClass());
            throw new AccessDeniedException("Invalid authentication");
        }

        JwtAuthenticationDetails details = (JwtAuthenticationDetails) authentication.getDetails();
        UUID authenticatedApplicationId = details.getApplicationId();

        if (authenticatedApplicationId == null) {
            log.warn("No application ID found in JWT token");
            throw new AccessDeniedException("Invalid token");
        }

        if (!authenticatedApplicationId.equals(requestedApplicationId)) {
            log.warn("Authorization failed: Applicant {} attempted to access application {}",
                authenticatedApplicationId, requestedApplicationId);
            throw new AccessDeniedException("You are not authorized to access this application");
        }

        log.debug("Authorization successful: Applicant {} accessing own application", authenticatedApplicationId);
    }

    /**
     * Gets complete application details.
     * Requires authentication (JWT token).
     *
     * @param id Application ID
     * @return Complete application details
     */
    @GetMapping("/applications/{id}")
    @Operation(
            summary = "Get application details",
            description = "Retrieves complete details of the applicant's onboarding application. " +
                    "Requires JWT token obtained from OTP verification."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Application details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
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

        log.info("Retrieving application details for: {}", id);

        // Authorization check: ensure applicant can only access their own application
        validateApplicantAuthorization(id);

        OnboardingApplication application = onboardingRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found with ID: " + id));

        ApplicationResponse response = mapper.toApplicationResponse(application);

        log.info("Application details retrieved for: {}", id);

        return ResponseEntity.ok(response);
    }

    /**
     * Uploads a document (passport or photo).
     * Supports multipart file upload.
     *
     * @param id Application ID
     * @param file Document file
     * @param documentType Type of document
     * @return Document upload response
     */
    @PostMapping(value = "/applications/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload document",
            description = "Uploads a document (passport or photo) for the application. " +
                    "Supports image files (JPEG, PNG) and PDF. Maximum file size: 10MB."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Document upload accepted and processing",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file or file too large",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<DocumentResponse>> uploadDocument(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) {

        log.info("Uploading document type {} for application: {}", documentType, id);

        // Authorization check: ensure applicant can only access their own application
        validateApplicantAuthorization(id);

        try {
            // Validate file
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File cannot be empty");
            }

            if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
                throw new IllegalArgumentException("File size exceeds maximum limit of 10MB");
            }

            // Validate content type
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
                throw new IllegalArgumentException("Invalid file type. Only images (JPEG, PNG) and PDF are allowed");
            }

            // Create upload command
            UploadDocumentUseCase.UploadDocumentCommand command =
                    new UploadDocumentUseCase.UploadDocumentCommand(
                            id,
                            documentType,
                            file.getBytes(),
                            file.getOriginalFilename(),
                            contentType
                    );

            // Upload document (async)
            return onboardingService.uploadDocument(command)
                    .thenApply(documentId -> {
                        log.info("Document uploaded successfully for application: {}", id);

                        // Retrieve the application to get document details
                        OnboardingApplication application = onboardingRepository
                                .findById(id)
                                .orElseThrow(() -> new RuntimeException("Application not found"));

                        // Find the uploaded document
                        ApplicationDocument document = application.getDocuments().stream()
                                .filter(doc -> doc.getId().equals(documentId))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Document not found"));

                        DocumentResponse response = mapper.toDocumentResponse(document);

                        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
                    })
                    .exceptionally(ex -> {
                        log.error("Failed to upload document for application: {}", id, ex);
                        throw new RuntimeException(ex);
                    });

        } catch (IOException e) {
            log.error("Failed to read file content for application: {}", id, e);
            throw new RuntimeException("Failed to read file content", e);
        }
    }

    /**
     * Submits application for compliance review.
     * Application must have all required documents uploaded.
     *
     * @param id Application ID
     * @return Success message
     */
    @PostMapping("/applications/{id}/submit")
    @Operation(
            summary = "Submit application for review",
            description = "Submits the application for compliance review. " +
                    "All required documents (passport and photo) must be uploaded before submission."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Application submitted successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing required documents or consents",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> submitApplication(
            @PathVariable("id") UUID id) {

        log.info("Submitting application for review: {}", id);

        // Authorization check: ensure applicant can only access their own application
        validateApplicantAuthorization(id);

        SubmitApplicationUseCase.SubmitApplicationCommand command =
                new SubmitApplicationUseCase.SubmitApplicationCommand(id);

        return onboardingService.submitForReview(command)
                .thenApply(message -> {
                    log.info("Application submitted successfully: {}", id);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to submit application: {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Provides additional information when requested by compliance officer.
     * Application must be in REQUIRES_MORE_INFO status.
     *
     * @param id Application ID
     * @param request Additional information provided by applicant
     * @return Success message
     */
    @PostMapping("/applications/{id}/provide-info")
    @Operation(
            summary = "Provide additional information",
            description = "Provides additional information in response to compliance officer request. " +
                    "Application must be in REQUIRES_MORE_INFO status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Additional information provided successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Application not in REQUIRES_MORE_INFO status",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<ApiResponseDto>> provideAdditionalInfo(
            @PathVariable("id") UUID id,
            @RequestBody ProvideInfoRequest request) {

        log.info("Providing additional info for application: {}", id);

        // Authorization check: ensure applicant can only access their own application
        validateApplicantAuthorization(id);

        ProvideAdditionalInfoUseCase.ProvideInfoCommand command =
                new ProvideAdditionalInfoUseCase.ProvideInfoCommand(id, request.information());

        return onboardingService.provideAdditionalInfo(command)
                .thenApply(message -> {
                    log.info("Additional info provided successfully for application: {}", id);
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(ApiResponseDto.success(message));
                })
                .exceptionally(ex -> {
                    log.error("Failed to provide additional info for application: {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Exports all personal data (GDPR Article 15 - Right to Data Portability).
     * Returns JSON file with all personal information.
     *
     * @param id Application ID
     * @return JSON file with personal data
     */
    @GetMapping(value = "/applications/{id}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Export personal data (GDPR)",
            description = "Exports all personal data associated with the application in JSON format. " +
                    "Implements GDPR Article 15 - Right to Data Portability."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Personal data exported successfully",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT token",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<String>> exportPersonalData(
            @PathVariable("id") UUID id) {

        log.info("Exporting personal data for application: {}", id);

        // Authorization check: ensure applicant can only access their own application
        validateApplicantAuthorization(id);

        ExportPersonalDataUseCase.ExportDataCommand command =
                new ExportPersonalDataUseCase.ExportDataCommand(id);

        return gdprService.exportData(command)
                .thenApply(jsonData -> {
                    log.info("Personal data exported successfully for application: {}", id);

                    // Set headers for file download
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentDispositionFormData("attachment",
                            "personal-data-" + id + ".json");

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(jsonData);
                })
                .exceptionally(ex -> {
                    log.error("Failed to export personal data for application: {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }
}
