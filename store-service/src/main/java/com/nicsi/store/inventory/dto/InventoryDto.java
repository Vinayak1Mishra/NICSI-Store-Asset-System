package com.nicsi.store.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InventoryDto {

    public record BalanceResponse(
            UUID id,
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            UUID categoryId,
            String categoryName,
            UUID storeId,
            String storeCode,
            String storeName,
            UUID locationId,
            String locationCode,
            String locationName,
            UUID lotId,
            String lotNumber,
            LocalDate lotExpiryDate,
            BigDecimal onHandQty,
            BigDecimal reservedQty,
            BigDecimal availableQty,
            BigDecimal avgUnitCost,
            BigDecimal inventoryValue,
            BigDecimal reorderLevelQty,
            boolean lowStock,
            Instant updatedAt,
            Long version
    ) {}

    public record LedgerResponse(
            UUID id,
            String transactionNo,
            UUID movementGroupId,
            String transactionType,
            Instant transactionTime,
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            UUID storeId,
            String storeCode,
            String storeName,
            UUID locationId,
            String locationCode,
            String locationName,
            UUID lotId,
            String lotNumber,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String referenceType,
            UUID referenceId,
            String referenceNo,
            String idempotencyKey,
            String remarks,
            UUID postedBy,
            Instant postedAt
    ) {}

    public record LowStockResponse(
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            UUID storeId,
            String storeCode,
            String storeName,
            BigDecimal onHandQty,
            BigDecimal reservedQty,
            BigDecimal availableQty,
            BigDecimal reorderLevelQty,
            BigDecimal reorderQty,
            BigDecimal minStockQty
    ) {}

    public record ReservationResponse(
            UUID id,
            String reservationNo,
            UUID requisitionItemId,
            UUID requisitionId,
            String requisitionNo,
            UUID itemId,
            String itemCode,
            String itemName,
            String uomCode,
            UUID storeId,
            String storeCode,
            String storeName,
            UUID locationId,
            String locationCode,
            BigDecimal reservedQty,
            BigDecimal consumedQty,
            String status,
            Instant expiresAt,
            Instant createdAt,
            UUID createdBy,
            Instant releasedAt,
            UUID releasedBy
    ) {}

    public record ReconciliationResponse(
            boolean reconciled,
            Instant checkedAt,
            int totalBalancesChecked,
            int totalLedgerTransactionsChecked,
            int totalActiveAssetsChecked,
            List<Discrepancy> discrepancies
    ) {}

    public record Discrepancy(
            String type,
            UUID itemId,
            String itemCode,
            String itemName,
            UUID storeId,
            String storeCode,
            UUID locationId,
            String locationCode,
            BigDecimal ledgerCalculatedQty,
            BigDecimal balanceOnHandQty,
            BigDecimal stockBalanceQty,
            Long activeAssetCount,
            String description
    ) {}
}
