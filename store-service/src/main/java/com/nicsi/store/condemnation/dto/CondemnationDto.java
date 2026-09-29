package com.nicsi.store.condemnation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class CondemnationDto {

    public record CreateLine(
            @NotNull UUID assetId,
            String assessedCondition,
            BigDecimal residualValue,
            String recommendedMethod,
            String remarks
    ) {}

    public record CreateRequest(
            @NotBlank String technicalReason,
            String committeeReference,
            LocalDate proposalDate,
            @NotEmpty List<CreateLine> items
    ) {}

    public record Response(
            UUID id,
            String condemnationNo,
            LocalDate proposalDate,
            String committeeReference,
            String technicalReason,
            String status,
            int itemCount,
            Instant approvedAt,
            UUID approvedBy,
            Instant createdAt,
            UUID createdBy,
            List<ItemResponse> items
    ) {}

    public record ItemResponse(
            UUID id,
            UUID assetId,
            String assetCode,
            String itemName,
            String serialNumber,
            String assessedCondition,
            BigDecimal residualValue,
            String recommendedMethod,
            String remarks
    ) {}
}
