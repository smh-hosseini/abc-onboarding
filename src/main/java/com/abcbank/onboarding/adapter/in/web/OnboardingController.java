package com.abcbank.onboarding.adapter.in.web;

import com.abcbank.onboarding.adapter.in.web.dto.*;
import com.abcbank.onboarding.adapter.in.web.mapper.OnboardingDtoMapper;
import com.abcbank.onboarding.application.OnboardingApplicationService;
import com.abcbank.onboarding.application.OtpApplicationService;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.port.in.CreateApplicationUseCase;
import com.abcbank.onboarding.domain.port.in.VerifyOtpUseCase;
import com.abcbank.onboarding.domain.port.out.OnboardingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for public onboarding endpoints.
 * These endpoints are accessible without authentication.
 *
 * Endpoints:
 * - POST /api/v1/onboarding/applications - Create new application
 * - POST /api/v1/onboarding/applications/{id}/send-otp - Send OTP
 * - POST /api/v1/onboarding/applications/{id}/verify-otp - Verify OTP (returns JWT)
 * - GET /api/v1/onboarding/applications/{id}/status - Get application status
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "Public onboarding endpoints for creating and verifying applications")
public class OnboardingController {

    private final OnboardingApplicationService onboardingService;
    private final OtpApplicationService otpService;
    private final OnboardingRepository onboardingRepository;
    private final OnboardingDtoMapper mapper;

    /**
     * Creates a new onboarding application.
     * Validates applicant data and generates OTP.
     *
     * @param request OnboardingRequest with applicant details
     * @return ApplicationCreatedResponse with application ID
     */
    @PostMapping("/applications")
    @Operation(
            summary = "Create new onboarding application",
            description = "Creates a new customer onboarding application. Validates all personal information, " +
                    "checks for duplicates, and sends OTP to applicant's email and phone."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Application created successfully",
                    content = @Content(schema = @Schema(implementation = ApplicationCreatedResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or validation failed",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate customer detected",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<ApplicationCreatedResponse> createApplication(
            @Valid @RequestBody OnboardingRequest request) {

        log.info("Received application request for email: {}", request.email());

        try {
            // Convert DTO to command
            CreateApplicationUseCase.CreateApplicationCommand command = mapper.toCreateCommand(request);

            // Create application (synchronous)
            UUID applicationId = onboardingService.execute(command);

            log.info("Successfully created application with ID: {}", applicationId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApplicationCreatedResponse(
                            applicationId,
                            "Application created successfully. OTP has been sent to your email and phone."
                    ));

        } catch (Exception e) {
            log.error("Failed to create application for email: {}", request.email(), e);
            throw e;
        }
    }

    /**
     * Sends OTP to applicant via specified channel.
     *
     * @param id Application ID
     * @param request SendOtpRequest with channel specification
     * @return Success message with expiry time
     */
    @PostMapping("/applications/{id}/send-otp")
    @Operation(
            summary = "Send OTP to applicant",
            description = "Sends a new OTP code to the applicant via the specified channel (EMAIL or SMS). " +
                    "Each channel can be verified independently."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "OTP sent successfully",
                    content = @Content(schema = @Schema(implementation = OtpSentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid channel specified",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public CompletableFuture<ResponseEntity<OtpSentResponse>> sendOtp(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SendOtpRequest request) {

        log.info("Sending OTP for application: {} via channel: {}", id, request.channel());

        return otpService.sendOtp(id, convertChannel(request.channel()))
                .thenApply(expiresIn -> {
                    log.info("OTP sent successfully for application: {} via {}", id, request.channel());
                    String message = String.format("OTP sent successfully to your %s",
                            request.channel() == SendOtpRequest.OtpChannel.EMAIL ? "email" : "phone");
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(new OtpSentResponse(message, expiresIn));
                })
                .exceptionally(ex -> {
                    log.error("Failed to send OTP for application: {}", id, ex);
                    throw new RuntimeException(ex);
                });
    }

    /**
     * Verifies OTP code and returns JWT token.
     *
     * @param id Application ID
     * @param request OTP verification request
     * @return JWT token for authenticated session
     */
    @PostMapping("/applications/{id}/verify-otp")
    @Operation(
            summary = "Verify OTP code",
            description = "Verifies the OTP code sent to applicant. If verification succeeds, " +
                    "returns a JWT token that can be used to access authenticated endpoints."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OTP verified successfully",
                    content = @Content(schema = @Schema(implementation = JwtTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid OTP or OTP expired",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Maximum OTP verification attempts exceeded",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<JwtTokenResponse> verifyOtp(
            @PathVariable("id") UUID id,
            @Valid @RequestBody OtpVerificationRequest request) {

        log.info("Verifying OTP for application: {}", id);

        try {
            // Create command
            VerifyOtpUseCase.VerifyOtpCommand command =
                    new VerifyOtpUseCase.VerifyOtpCommand(id, request.otp(), convertChannel(request.channel()));

            // Verify OTP (synchronous)
            String jwtToken = otpService.execute(command);

            log.info("OTP verified successfully for application: {}", id);

            // Return JWT token with 1 hour expiration
            return ResponseEntity.ok(
                    new JwtTokenResponse(jwtToken, id, 3600)
            );

        } catch (Exception e) {
            log.error("Failed to verify OTP for application: {}", id, e);
            throw e;
        }
    }

    /**
     * Gets application status (lightweight).
     * Public endpoint for checking status without authentication.
     *
     * @param id Application ID
     * @return Application status
     */
    @GetMapping("/applications/{id}/status")
    @Operation(
            summary = "Get application status",
            description = "Retrieves the current status of an application. " +
                    "This is a lightweight endpoint that returns only status information."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApplicationStatusResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Application not found",
                    content = @Content(schema = @Schema(implementation = ApiResponseDto.class))
            )
    })
    public ResponseEntity<ApplicationStatusResponse> getApplicationStatus(
            @PathVariable("id") UUID id) {

        log.info("Retrieving status for application: {}", id);

        OnboardingApplication application = onboardingRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found with ID: " + id));

        ApplicationStatusResponse response = mapper.toStatusResponse(application);

        log.info("Status retrieved for application: {} - Status: {}", id, response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to convert DTO channel enum to domain channel enum.
     */
    private com.abcbank.onboarding.domain.model.OtpVerification.OtpChannel convertChannel(SendOtpRequest.OtpChannel dtoChannel) {
        return com.abcbank.onboarding.domain.model.OtpVerification.OtpChannel.valueOf(dtoChannel.name());
    }
}
