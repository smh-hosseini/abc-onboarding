package com.abcbank.onboarding.adapter.in.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for GDPR data export.
 */
public record GdprExportResponse(
        String applicationId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt,
        PersonalInfoExport personalInformation,
        AddressExport residentialAddress,
        List<DocumentExport> documents,
        List<ConsentExport> consents,
        AccountInfoExport accountInformation,
        GdprMetadataExport gdprInformation
) {
    public record PersonalInfoExport(
            String firstName,
            String lastName,
            String gender,
            LocalDate dateOfBirth,
            String email,
            String phone,
            String nationality,
            String socialSecurityNumber
    ) {}

    public record AddressExport(
            String street,
            String houseNumber,
            String postalCode,
            String city,
            String country
    ) {}

    public record DocumentExport(
            String documentId,
            String documentType,
            LocalDateTime uploadedAt,
            String status,
            LocalDateTime verifiedAt,
            String verifiedBy
    ) {}

    public record ConsentExport(
            String consentType,
            LocalDateTime grantedAt,
            boolean active
    ) {}

    public record AccountInfoExport(
            String customerId,
            String accountNumber
    ) {}

    public record GdprMetadataExport(
            LocalDateTime dataRetentionUntil,
            boolean markedForDeletion,
            LocalDateTime exportedAt,
            String exportFormat,
            String gdprArticle
    ) {}
}
