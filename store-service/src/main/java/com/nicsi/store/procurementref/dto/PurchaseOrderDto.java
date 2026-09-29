package com.nicsi.store.procurementref.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseOrderDto {

    private PurchaseOrderDto() {}

    public record CreateItemRequest(
            @NotNull Integer poLineNo,
            UUID itemId,
            String itemDescription,
            @NotNull @Positive BigDecimal orderedQty,
            BigDecimal unitRate,
            BigDecimal taxAmount,
            LocalDate deliveryDueDate,
            UUID projectId,
            String projectCodeSnapshot,
            String projectNameSnapshot
    ) {}

    public record CreateRequest(
            String sourceSystem,
            UUID sourcePoId,
            @NotBlank @Size(max = 80) String poNumber,
            LocalDate poDate,
            String procurementMode,
            String gemOrderNumber,
            String contractNumber,
            UUID vendorId,
            String vendorCodeSnapshot,
            String vendorNameSnapshot,
            String currencyCode,
            BigDecimal totalAmount,
            String rawSnapshot,
            @NotEmpty @Valid List<CreateItemRequest> items
    ) {}

    public record ItemResponse(
            UUID id,
            Integer poLineNo,
            UUID itemId,
            String itemCode,
            String itemName,
            String itemDescription,
            BigDecimal orderedQty,
            BigDecimal receivedQty,
            BigDecimal remainingQty,
            BigDecimal unitRate,
            BigDecimal taxAmount,
            LocalDate deliveryDueDate,
            UUID projectId,
            String projectCodeSnapshot,
            String projectNameSnapshot
    ) {}

    public record Response(
            UUID id,
            String sourceSystem,
            UUID sourcePoId,
            String poNumber,
            LocalDate poDate,
            String procurementMode,
            String gemOrderNumber,
            String contractNumber,
            UUID vendorId,
            String vendorCodeSnapshot,
            String vendorNameSnapshot,
            String currencyCode,
            BigDecimal totalAmount,
            String status,
            String rawSnapshot,
            Instant createdAt,
            Instant updatedAt,
            List<ItemResponse> items
    ) {}

    public record SummaryResponse(
            UUID id,
            String sourceSystem,
            String poNumber,
            LocalDate poDate,
            String procurementMode,
            String gemOrderNumber,
            String vendorNameSnapshot,
            String currencyCode,
            BigDecimal totalAmount,
            String status,
            int itemCount,
            Instant createdAt
    ) {}
}
