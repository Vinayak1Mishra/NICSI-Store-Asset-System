package com.nicsi.store.condemnation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class CondemnationDto {

    /**
     * Permitted store.asset.condition_status values, from asset_condition_status_check
     * in V5__fulfilment_asset_lifecycle.sql. The value is copied verbatim into that
     * column by CondemnationService.approve, so an unvalidated string reached the
     * database and any typo failed the check constraint with a 500 at approve time,
     * long after the request that caused it was accepted with a 201.
     */
    public static final String CONDITION_STATUS_PATTERN =
            "NEW|GOOD|WORKING|FAIR|DAMAGED|REPAIR_REQUIRED|UNSERVICEABLE|SCRAP";

    public record CreateLine(
            @NotNull UUID assetId,
            @Pattern(regexp = CONDITION_STATUS_PATTERN,
                     message = "assessedCondition must be one of: " + CONDITION_STATUS_PATTERN)
            String assessedCondition,
            BigDecimal residualValue,
            @Pattern(regexp = "AUCTION|E_WASTE|SCRAP|RETURN_TO_OEM|TRANSFER|OTHER",
                     message = "recommendedMethod must be one of: AUCTION, E_WASTE, SCRAP, RETURN_TO_OEM, TRANSFER, OTHER")
            String recommendedMethod,
            String remarks
    ) {}

    public record CreateRequest(
            @NotBlank String technicalReason,
            String committeeReference,
            LocalDate proposalDate,
            // @Valid is required for the constraints on CreateLine to be evaluated.
            // Without it Bean Validation stops at this record, so a bad
            // assessedCondition/recommendedMethod reached the database and failed a
            // CHECK constraint instead of being reported as a 400.
            @NotEmpty List<@Valid CreateLine> items
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
