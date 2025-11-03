package com.abcbank.onboarding.adapter.in.web.mapper;

import com.abcbank.onboarding.adapter.in.web.dto.*;
import com.abcbank.onboarding.domain.model.Address;
import com.abcbank.onboarding.domain.model.ApplicationDocument;
import com.abcbank.onboarding.domain.model.OnboardingApplication;
import com.abcbank.onboarding.domain.port.in.CreateApplicationUseCase;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Mapper component for converting between DTOs and domain models.
 * Handles data transformation for the web layer.
 */
@Component
public class OnboardingDtoMapper {

    /**
     * Converts OnboardingRequest DTO to CreateApplicationCommand.
     *
     * @param request OnboardingRequest DTO from API
     * @return CreateApplicationCommand for use case
     */
    public CreateApplicationUseCase.CreateApplicationCommand toCreateCommand(OnboardingRequest request) {
        return new CreateApplicationUseCase.CreateApplicationCommand(
                request.firstName(),
                request.lastName(),
                request.gender(),
                request.dateOfBirth(),
                request.phone(),
                request.email(),
                request.nationality(),
                toAddress(request.residentialAddress()),
                request.socialSecurityNumber()
        );
    }

    /**
     * Converts AddressRequest DTO to Address domain model.
     *
     * @param request AddressRequest DTO
     * @return Address domain model
     */
    public Address toAddress(AddressRequest request) {
        if (request == null) {
            return null;
        }
        return new Address(
                request.street(),
                request.houseNumber(),
                request.postalCode(),
                request.city(),
                request.country()
        );
    }

    /**
     * Converts Address domain model to AddressResponse DTO.
     *
     * @param address Address domain model
     * @return AddressResponse DTO
     */
    public AddressResponse toAddressResponse(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressResponse(
                address.getStreet(),
                address.getHouseNumber(),
                address.getPostalCode(),
                address.getCity(),
                address.getCountry()
        );
    }

    /**
     * Converts OnboardingApplication domain model to ApplicationResponse DTO.
     * Masks sensitive data (SSN) and excludes OTP information.
     *
     * @param application OnboardingApplication domain model
     * @return ApplicationResponse DTO
     */
    public ApplicationResponse toApplicationResponse(OnboardingApplication application) {
        if (application == null) {
            return null;
        }
        return new ApplicationResponse(
                application.getId(),
                application.getStatus(),
                application.getFirstName(),
                application.getLastName(),
                application.getGender(),
                application.getDateOfBirth(),
                application.getPhone(),
                application.getEmail(),
                application.getNationality(),
                toAddressResponse(application.getResidentialAddress()),
                maskSsn(application.getSocialSecurityNumber()),
                application.getDocuments().stream()
                        .map(this::toDocumentResponse)
                        .collect(Collectors.toList()),
                application.getAccountNumber(),
                application.getAssignedTo(),
                application.isRequiresManualReview(),
                application.getReviewReason(),
                application.getRejectionReason(),
                application.getCreatedAt(),
                application.getSubmittedAt(),
                application.getApprovedAt(),
                application.getRejectedAt()
        );
    }

    /**
     * Converts OnboardingApplication to ApplicationStatusResponse (lightweight).
     *
     * @param application OnboardingApplication domain model
     * @return ApplicationStatusResponse DTO
     */
    public ApplicationStatusResponse toStatusResponse(OnboardingApplication application) {
        if (application == null) {
            return null;
        }

        // Determine last updated timestamp
        LocalDateTime lastUpdated = application.getCreatedAt();
        if (application.getSubmittedAt() != null) {
            lastUpdated = application.getSubmittedAt();
        }
        if (application.getApprovedAt() != null) {
            lastUpdated = application.getApprovedAt();
        }
        if (application.getRejectedAt() != null) {
            lastUpdated = application.getRejectedAt();
        }

        return new ApplicationStatusResponse(
                application.getId(),
                application.getStatus(),
                application.getCreatedAt(),
                lastUpdated
        );
    }

    /**
     * Converts ApplicationDocument to DocumentResponse DTO.
     *
     * @param document ApplicationDocument domain model
     * @return DocumentResponse DTO
     */
    public DocumentResponse toDocumentResponse(ApplicationDocument document) {
        if (document == null) {
            return null;
        }
        return new DocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getStatus(),
                document.getMimeType(),
                document.getFileSize(),
                document.getUploadedAt(),
                document.getVerifiedAt(),
                document.getVerifiedBy(),
                document.getRejectionReason()
        );
    }

    /**
     * Masks Social Security Number to show only last 4 digits.
     * Format: XXX-XX-1234
     *
     * @param ssn Social Security Number (9 digits)
     * @return Masked SSN
     */
    private String maskSsn(String ssn) {
        if (ssn == null || ssn.length() != 9) {
            return "XXX-XX-XXXX";
        }
        String lastFour = ssn.substring(5);
        return "XXX-XX-" + lastFour;
    }
}
