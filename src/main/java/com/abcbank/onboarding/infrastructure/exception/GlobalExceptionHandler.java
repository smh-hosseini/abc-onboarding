package com.abcbank.onboarding.infrastructure.exception;

import com.abcbank.onboarding.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler implementing RFC 7807 Problem Details for HTTP APIs
 * Principle: Never leak implementation details to clients
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_BASE_URL = "https://api.abc.nl/errors/";

    // ========== Domain Exceptions ==========

    @ExceptionHandler(DuplicateCustomerException.class)
    public ProblemDetail handleDuplicateCustomer(DuplicateCustomerException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Duplicate customer detected, traceId: {}", traceId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A customer with the provided information already exists");
        problem.setType(URI.create(ERROR_BASE_URL + "duplicate-customer"));
        problem.setTitle("Duplicate Customer");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ProblemDetail handleOtpExpired(OtpExpiredException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("OTP expired, traceId: {}", traceId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URL + "otp-expired"));
        problem.setTitle("OTP Expired");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    @ExceptionHandler(OtpMaxAttemptsException.class)
    public ProblemDetail handleOtpMaxAttempts(OtpMaxAttemptsException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("OTP max attempts exceeded, traceId: {}", traceId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URL + "otp-max-attempts"));
        problem.setTitle("Rate Limit Exceeded");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidStatusTransition(InvalidStatusTransitionException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Invalid status transition, traceId: {}", traceId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "The requested operation is not allowed in the current application state");
        problem.setType(URI.create(ERROR_BASE_URL + "invalid-state"));
        problem.setTitle("Invalid State Transition");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Business rule violation, traceId: {}", traceId, ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URL + "business-rule-violation"));
        problem.setTitle("Business Rule Violation");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Resource not found, traceId: {}", traceId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The requested resource was not found");
        problem.setType(URI.create(ERROR_BASE_URL + "not-found"));
        problem.setTitle("Resource Not Found");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    // ========== Validation Exceptions ==========

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Validation failed, traceId: {}", traceId);

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request contains invalid data");
        problem.setType(URI.create(ERROR_BASE_URL + "validation-error"));
        problem.setTitle("Validation Failed");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);
        problem.setProperty("errors", errors);

        return problem;
    }

    // ========== Security Exceptions ==========

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Authentication failed, traceId: {}", traceId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Authentication failed");
        problem.setType(URI.create(ERROR_BASE_URL + "unauthorized"));
        problem.setTitle("Unauthorized");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Access denied, traceId: {}", traceId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You don't have permission to access this resource");
        problem.setType(URI.create(ERROR_BASE_URL + "forbidden"));
        problem.setTitle("Forbidden");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    // ========== Rate Limiting ==========

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Rate limit exceeded, traceId: {}", traceId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later");
        problem.setType(URI.create(ERROR_BASE_URL + "rate-limit"));
        problem.setTitle("Rate Limit Exceeded");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }

    // ========== Generic Exception ==========

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();

        // Log full exception details internally (with stack trace)
        log.error("Internal server error, traceId: {}", traceId, ex);

        // Return generic error to client (NO IMPLEMENTATION DETAILS)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support with the trace ID");
        problem.setType(URI.create(ERROR_BASE_URL + "internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceId);

        return problem;
    }
}
