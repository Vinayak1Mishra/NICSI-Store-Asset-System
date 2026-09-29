package com.nicsi.store.grn.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GrnDto {

    private GrnDto() {}

    public record CreateItemRequest(
            UUID poItemRefId,
            @NotNull UUID itemId,
            @NotNull @Positive BigDecimal receivedQty,
            BigDecimal unitRate,
            @NotNull UUID receivingLocationId,
            @Size(max = 120) String batchLotNo,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            String remarks
    ) {}

    public record CreateRequest(
            LocalDate grnDate,
            @NotNull UUID storeId,
            UUID poRefId,
            UUID vendorId,
            @Size(max = 250) String vendorNameSnapshot,
            @Size(max = 100) String invoiceNumber,
            LocalDate invoiceDate,
            @Size(max = 100) String challanNumber,
            LocalDate challanDate,
            String remarks,
            @NotEmpty @Valid List<CreateItemRequest> items
    ) {}

    public record UpdateRequest(
            LocalDate grnDate,
            @NotNull UUID storeId,
            UUID poRefId,
            UUID vendorId,
            @Size(max = 250) String vendorNameSnapshot,
            @Size(max = 100) String invoiceNumber,
            LocalDate invoiceDate,
            @Size(max = 100) String challanNumber,
            LocalDate challanDate,
            String remarks,
            @NotEmpty @Valid List<CreateItemRequest> items,
            @NotNull Long version
    ) {}

    public record ItemResponse(
            UUID id,
            Integer lineNo,
            UUID poItemRefId,
            Integer poLineNo,
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            BigDecimal receivedQty,
            BigDecimal acceptedQty,
            BigDecimal rejectedQty,
            BigDecimal unitRate,
            UUID receivingLocationId,
            String receivingLocationCode,
            String receivingLocationName,
            String batchLotNo,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            String remarks
    ) {}

    public record Response(
            UUID id,
            String grnNo,
            LocalDate grnDate,
            UUID storeId,
            String storeCode,
            String storeName,
            UUID poRefId,
            String poNumber,
            UUID vendorId,
            String vendorNameSnapshot,
            String invoiceNumber,
            LocalDate invoiceDate,
            String challanNumber,
            LocalDate challanDate,
            UUID receivedByUserId,
            String status,
            String remarks,
            Instant createdAt,
            UUID createdBy,
            Instant updatedAt,
            UUID updatedBy,
            Instant approvedAt,
            UUID approvedBy,
            Long version,
            List<ItemResponse> items
    ) {}

    public record SummaryResponse(
            UUID id,
            String grnNo,
            LocalDate grnDate,
            UUID storeId,
            String storeName,
            UUID poRefId,
            String poNumber,
            String vendorNameSnapshot,
            String invoiceNumber,
            String challanNumber,
            String status,
            int itemCount,
            BigDecimal totalReceivedQty,
            Instant createdAt
    ) {}
}
