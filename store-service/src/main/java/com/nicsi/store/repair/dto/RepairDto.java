package com.nicsi.store.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class RepairDto {

    public record CreateRequest(
            @NotNull UUID assetId,
            @NotBlank String complaintDetail,
            boolean warrantyClaim,
            String vendorNameSnapshot,
            LocalDate expectedReturnDate,
            String remarks
    ) {}

    public record UpdateRequest(
            String status,
            String diagnosis,
            String repairAction,
            String partsReplaced,
            BigDecimal repairCost,
            LocalDate sentDate,
            LocalDate expectedReturnDate,
            LocalDate receivedDate,
            String finalCondition,
            String dispatchChallanNo,
            String vendorNameSnapshot
    ) {}

    public record Response(
            UUID id,
            String repairNo,
            UUID assetId,
            String assetCode,
            String itemName,
            LocalDate complaintDate,
            String complaintDetail,
            boolean warrantyClaim,
            String vendorNameSnapshot,
            String dispatchChallanNo,
            LocalDate sentDate,
            LocalDate expectedReturnDate,
            LocalDate receivedDate,
            String diagnosis,
            String repairAction,
            String partsReplaced,
            BigDecimal repairCost,
            String status,
            String finalCondition,
            Instant createdAt,
            UUID createdBy,
            Instant updatedAt,
            Long version
    ) {}

    public record SummaryResponse(
            UUID id,
            String repairNo,
            UUID assetId,
            String assetCode,
            String itemName,
            LocalDate complaintDate,
            String status,
            boolean warrantyClaim,
            BigDecimal repairCost,
            Instant createdAt
    ) {}
}
