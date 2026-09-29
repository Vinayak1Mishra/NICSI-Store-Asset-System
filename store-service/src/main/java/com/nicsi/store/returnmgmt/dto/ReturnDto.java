package com.nicsi.store.returnmgmt.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ReturnDto {

    public record CreateRequest(
            @NotNull UUID storeId,
            LocalDate returnDate,
            UUID returnedByUserId,
            UUID departmentId,
            UUID projectId,
            String remarks,
            @NotEmpty List<LineRequest> items
    ) {}

    public record LineRequest(
            @NotNull UUID itemId,
            UUID assetId,
            @NotNull @Positive BigDecimal returnQty,
            @NotNull UUID returnLocationId,
            String conditionStatus,
            String remarks
    ) {}

    public record ReceiveRequest(
            UUID receivedByUserId,
            List<ReceiveLineRequest> lines
    ) {}

    public record ReceiveLineRequest(
            @NotNull UUID lineId,
            @NotNull String conditionStatus,
            @NotNull String disposition,
            UUID returnLocationId,
            String remarks
    ) {}

    public record PostRequest(
            String remarks
    ) {}

    public record SummaryResponse(
            UUID id,
            String returnNo,
            LocalDate returnDate,
            UUID storeId,
            String storeName,
            UUID returnedByUserId,
            UUID departmentId,
            UUID projectId,
            String status,
            int itemCount,
            Instant createdAt
    ) {}

    public record Response(
            UUID id,
            String returnNo,
            LocalDate returnDate,
            UUID storeId,
            String storeCode,
            String storeName,
            UUID returnedByUserId,
            UUID departmentId,
            UUID projectId,
            UUID receivedByUserId,
            String status,
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
            UUID assetId,
            String assetCode,
            BigDecimal returnQty,
            UUID returnLocationId,
            String locationCode,
            String conditionStatus,
            String disposition,
            String remarks
    ) {}
}
