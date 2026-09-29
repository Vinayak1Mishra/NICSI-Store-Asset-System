package com.nicsi.store.issue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for Issue header creation, posting, acknowledgement and response.
 */
public final class IssueDto {

    private IssueDto() {}

    // ─── Inbound ─────────────────────────────────────────────────────────────

    /**
     * Request to create an issue draft.
     * The caller supplies the store, recipient details, and one or more line items.
     */
    public record CreateRequest(
            LocalDate issueDate,

            @NotNull(message = "storeId is required")
            UUID storeId,

            /** Optional — links issue back to the originating requisition */
            UUID requisitionId,

            /**
             * EMPLOYEE | DEPARTMENT | PROJECT | LOCATION | OTHER
             */
            @NotBlank(message = "issuedToType is required")
            @Pattern(regexp = "EMPLOYEE|DEPARTMENT|PROJECT|LOCATION|OTHER",
                     message = "issuedToType must be EMPLOYEE, DEPARTMENT, PROJECT, LOCATION, or OTHER")
            String issuedToType,

            UUID issuedToUserId,
            String issuedToNameSnapshot,
            UUID departmentId,
            String departmentNameSnapshot,
            UUID projectId,
            String projectNameSnapshot,
            String purpose,

            @NotEmpty(message = "At least one line item is required")
            @Valid
            List<CreateItemRequest> items
    ) {}

    /**
     * One line item within the create-issue request.
     */
    public record CreateItemRequest(
            @Positive(message = "lineNo must be positive")
            int lineNo,

            @NotNull(message = "itemId is required")
            UUID itemId,

            UUID requisitionItemId,
            UUID reservationId,

            @NotNull(message = "locationId is required")
            UUID locationId,

            UUID lotId,

            @NotNull(message = "issueQty is required")
            @DecimalMin(value = "0.001", message = "issueQty must be greater than 0")
            BigDecimal issueQty,

            String remarks
    ) {}

    /**
     * Request to post a DRAFT/APPROVED issue (finalise stock deduction).
     * When posting, the caller may optionally specify which specific asset IDs
     * to assign per issue line (for serialised/asset items).
     */
    public record PostRequest(
            List<LineAssetRequest> lineAssets,
            String remarks
    ) {}

    /**
     * Asset IDs to bind to a specific issue line during posting.
     */
    public record LineAssetRequest(
            @NotNull UUID issueItemId,
            @NotEmpty List<UUID> assetIds
    ) {}

    /**
     * Request to digitally acknowledge receipt of issued goods/assets.
     */
    public record AcknowledgeRequest(
            @NotBlank(message = "acknowledgementStatus is required")
            @Pattern(regexp = "ACCEPTED|PARTIAL|REJECTED",
                     message = "acknowledgementStatus must be ACCEPTED, PARTIAL, or REJECTED")
            String acknowledgementStatus,
            String remarks
    ) {}

    // ─── Outbound ─────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            UUID id,
            String issueNo,
            LocalDate issueDate,
            UUID requisitionId,
            UUID storeId,
            String storeCode,
            String storeName,
            String issuedToType,
            UUID issuedToUserId,
            String issuedToNameSnapshot,
            UUID departmentId,
            String departmentNameSnapshot,
            UUID projectId,
            String projectNameSnapshot,
            String purpose,
            String status,
            UUID issuedByUserId,
            Instant acknowledgedAt,
            String acknowledgementStatus,
            Instant createdAt,
            Long version,
            List<ItemResponse> items
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResponse(
            UUID id,
            int lineNo,
            UUID itemId,
            String itemCode,
            String itemName,
            UUID locationId,
            String locationCode,
            UUID lotId,
            String lotNumber,
            BigDecimal issueQty,
            BigDecimal unitCost,
            String remarks
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryResponse(
            UUID id,
            String issueNo,
            LocalDate issueDate,
            UUID storeId,
            String storeCode,
            String issuedToType,
            String issuedToNameSnapshot,
            String status,
            int lineCount,
            Instant createdAt
    ) {}

    /**
     * Response returned from the POST /post endpoint.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostResult(
            UUID issueId,
            String issueNo,
            String status,
            int totalPostedLines,
            int totalAssetsAssigned,
            Instant postedAt
    ) {}
}
