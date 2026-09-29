package com.nicsi.store.license.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SoftwareLicenseDto {

    private SoftwareLicenseDto() {}

    public record CreateRequest(
            @NotNull(message = "itemId is required")
            UUID itemId,

            @NotBlank(message = "licenseCode is required")
            String licenseCode,

            UUID vendorId,

            @NotBlank(message = "licenseType is required")
            String licenseType,

            @NotNull(message = "entitlementQty is required")
            @DecimalMin(value = "0.001", message = "entitlementQty must be positive")
            BigDecimal entitlementQty,

            String licenseKeySecretRef,
            LocalDate purchaseDate,
            LocalDate startDate,
            LocalDate endDate,
            String poNumberSnapshot
    ) {}

    public record AllocateRequest(
            @NotBlank(message = "allocationType is required")
            String allocationType, // 'USER','DEVICE','SERVER','PROJECT'

            UUID userId,
            UUID assetId,
            String serverIdentifier,

            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0.001", message = "quantity must be positive")
            BigDecimal quantity,

            String remarks
    ) {}

    public record AllocationResponse(
            UUID id,
            UUID softwareLicenseId,
            String allocationType,
            UUID userId,
            UUID assetId,
            String assetCode,
            String serverIdentifier,
            BigDecimal quantity,
            Instant allocatedAt,
            UUID allocatedBy,
            Instant releasedAt,
            String status
    ) {}

    public record Response(
            UUID id,
            UUID itemId,
            String itemCode,
            String itemName,
            String licenseCode,
            UUID vendorId,
            String licenseType,
            BigDecimal entitlementQty,
            BigDecimal allocatedQty,
            BigDecimal availableQty,
            String licenseKeySecretRef,
            LocalDate purchaseDate,
            LocalDate startDate,
            LocalDate endDate,
            String poNumberSnapshot,
            String status,
            Instant createdAt,
            Long version,
            List<AllocationResponse> allocations
    ) {}

    public record SummaryResponse(
            UUID id,
            UUID itemId,
            String itemCode,
            String itemName,
            String licenseCode,
            String licenseType,
            BigDecimal entitlementQty,
            BigDecimal allocatedQty,
            BigDecimal availableQty,
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {}
}
