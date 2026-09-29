package com.nicsi.store.warranty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class WarrantyDto {

    public record CreateRequest(
            @NotBlank String contractType,
            String contractNumber,
            String vendorNameSnapshot,
            UUID itemId,
            UUID assetId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            String coverageDetail,
            String slaDetail,
            BigDecimal amount,
            Integer renewalReminderDays
    ) {}

    public record UpdateRequest(
            String status,
            LocalDate endDate,
            String coverageDetail,
            String slaDetail,
            BigDecimal amount
    ) {}

    public record Response(
            UUID id,
            String contractType,
            String contractNumber,
            String vendorNameSnapshot,
            UUID itemId,
            String itemName,
            UUID assetId,
            String assetCode,
            LocalDate startDate,
            LocalDate endDate,
            String coverageDetail,
            String slaDetail,
            BigDecimal amount,
            Integer renewalReminderDays,
            String status,
            long daysToExpiry,
            Instant createdAt,
            UUID createdBy,
            Instant updatedAt
    ) {}
}
