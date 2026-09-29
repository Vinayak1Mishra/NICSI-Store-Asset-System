package com.nicsi.store.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class StockAdjustmentDto {

    public record CreateRequest(
            @NotNull UUID storeId,
            @NotBlank String reasonCode,
            @NotBlank String reasonDetail,
            LocalDate adjustmentDate,
            @NotEmpty @Valid List<LineRequest> items
    ) {}

    public record LineRequest(
            @NotNull UUID itemId,
            @NotNull UUID locationId,
            UUID lotId,
            @NotBlank String direction, // 'IN' or 'OUT'
            @NotNull @DecimalMin(value = "0.001", message = "Quantity must be greater than zero") BigDecimal quantity,
            BigDecimal unitCost,
            UUID assetId,
            String remarks
    ) {}

    public record Response(
            UUID id,
            String adjustmentNo,
            LocalDate adjustmentDate,
            UUID storeId,
            String storeCode,
            String storeName,
            String reasonCode,
            String reasonDetail,
            String status,
            Instant createdAt,
            UUID createdBy,
            Instant approvedAt,
            UUID approvedBy,
            Instant postedAt,
            UUID postedBy,
            List<LineResponse> items
    ) {}

    public record LineResponse(
            UUID id,
            Integer lineNo,
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            UUID locationId,
            String locationCode,
            UUID lotId,
            String lotNumber,
            String direction,
            BigDecimal quantity,
            BigDecimal unitCost,
            UUID assetId,
            String remarks
    ) {}

    public record SummaryResponse(
            UUID id,
            String adjustmentNo,
            LocalDate adjustmentDate,
            UUID storeId,
            String storeCode,
            String storeName,
            String reasonCode,
            String status,
            int itemCount,
            Instant createdAt,
            Instant postedAt
    ) {}

    public record ReversalRequest(
            @NotBlank String reason
    ) {}
}
