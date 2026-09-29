package com.nicsi.store.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class VerificationDto {

    public record CreateRequest(
            @NotBlank String verificationName,
            @NotNull UUID storeId,
            @NotBlank String verificationType,
            @NotNull LocalDate startDate,
            String committeeReference
    ) {}

    public record CountSheetEntry(
            @NotNull UUID itemId,
            UUID assetId,
            @NotNull UUID locationId,
            UUID lotId,
            @NotNull BigDecimal bookQty
    ) {}

    public record RecordCountRequest(
            @NotNull UUID itemId,
            UUID assetId,
            @NotNull UUID locationId,
            @NotNull BigDecimal physicalQty,
            String conditionStatus,
            String remarks
    ) {}

    public record Response(
            UUID id,
            String verificationNo,
            String verificationName,
            UUID storeId,
            String storeName,
            String verificationType,
            Instant snapshotTime,
            LocalDate startDate,
            LocalDate endDate,
            String committeeReference,
            String status,
            int totalItems,
            int countedItems,
            Instant approvedAt,
            UUID approvedBy,
            Instant createdAt,
            UUID createdBy
    ) {}

    public record ItemResponse(
            UUID id,
            UUID itemId,
            String itemCode,
            String itemName,
            UUID locationId,
            String locationCode,
            BigDecimal bookQty,
            BigDecimal physicalQty,
            BigDecimal varianceQty,
            String resultStatus,
            String conditionStatus,
            Instant scannedAt,
            String remarks
    ) {}
}
