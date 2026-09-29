package com.nicsi.store.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class TransferDto {

    public record CreateRequest(
            @NotNull UUID sourceStoreId,
            @NotNull UUID destinationStoreId,
            String remarks,
            @NotNull @Valid List<ItemLine> items
    ) {}

    public record ItemLine(
            @NotNull UUID itemId,
            UUID assetId,
            UUID lotId,
            @NotNull UUID sourceLocationId,
            @NotNull UUID destinationLocationId,
            @NotNull @Positive BigDecimal transferQty,
            String remarks
    ) {}

    public record ReceiveRequest(
            @NotNull @Valid List<ReceivedLine> lines,
            String remarks
    ) {}

    public record ReceivedLine(@NotNull UUID lineId, @NotNull BigDecimal receivedQty) {}

    public record Response(
            UUID id,
            String transferNo,
            LocalDate transferDate,
            UUID sourceStoreId,
            String sourceStoreName,
            UUID destinationStoreId,
            String destinationStoreName,
            UUID requestedByUserId,
            UUID approvedByUserId,
            String status,
            LocalDate dispatchDate,
            LocalDate receiveDate,
            String remarks,
            List<ItemResponse> items,
            Instant createdAt,
            UUID createdBy,
            Instant updatedAt,
            Long version
    ) {}

    public record ItemResponse(
            UUID id,
            Integer lineNo,
            UUID itemId,
            String itemCode,
            String itemName,
            UUID sourceLocationId,
            String sourceLocationCode,
            UUID destinationLocationId,
            String destinationLocationCode,
            BigDecimal transferQty,
            BigDecimal receivedQty,
            String remarks
    ) {}

    public record SummaryResponse(
            UUID id,
            String transferNo,
            LocalDate transferDate,
            UUID sourceStoreId,
            String sourceStoreName,
            UUID destinationStoreId,
            String destinationStoreName,
            String status,
            int itemCount,
            Instant createdAt
    ) {}
}
