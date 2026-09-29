package com.nicsi.store.inspection.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InspectionDto {

    private InspectionDto() {}

    public record CreateRequest(
            @NotNull UUID grnId,
            LocalDate inspectionDate,
            String overallRemarks
    ) {}

    public record DecideItemRequest(
            @NotNull UUID inspectionItemId,
            @NotNull @PositiveOrZero BigDecimal acceptedQty,
            @NotNull @PositiveOrZero BigDecimal rejectedQty,
            @NotNull @PositiveOrZero BigDecimal quarantineQty,
            Boolean specificationMatch,
            @Size(max = 20) String physicalCondition,
            Boolean warrantyVerified,
            Boolean accessoryVerified,
            String technicalResult,
            String remarks
    ) {}

    public record DecideRequest(
            String overallRemarks,
            @NotEmpty @Valid List<DecideItemRequest> items,
            @NotNull Long version
    ) {}

    public record ItemResponse(
            UUID id,
            UUID grnItemId,
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            BigDecimal inspectedQty,
            BigDecimal acceptedQty,
            BigDecimal rejectedQty,
            BigDecimal quarantineQty,
            Boolean specificationMatch,
            String physicalCondition,
            Boolean warrantyVerified,
            Boolean accessoryVerified,
            String technicalResult,
            String remarks
    ) {}

    public record Response(
            UUID id,
            String inspectionNo,
            UUID grnId,
            String grnNo,
            LocalDate inspectionDate,
            UUID inspectedByUserId,
            String inspectedByUserName,
            String status,
            String overallRemarks,
            UUID approvedBy,
            Instant approvedAt,
            Instant createdAt,
            UUID createdBy,
            Instant updatedAt,
            UUID updatedBy,
            Long version,
            List<ItemResponse> items
    ) {}

    public record SummaryResponse(
            UUID id,
            String inspectionNo,
            UUID grnId,
            String grnNo,
            LocalDate inspectionDate,
            UUID inspectedByUserId,
            String status,
            String overallRemarks,
            int itemCount,
            Instant createdAt
    ) {}
}
