package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ItemStorePolicyDto {
    private ItemStorePolicyDto() {}
    
    public record CreateRequest(
        @NotNull UUID itemId,
        @NotNull UUID storeId,
        @NotNull BigDecimal minStockQty,
        BigDecimal maxStockQty,
        @NotNull BigDecimal reorderLevelQty,
        @NotNull BigDecimal reorderQty,
        @NotNull Boolean allowNegativeStock,
        @NotBlank @Size(max = 20) String valuationMethod,
        UUID defaultLocationId
    ) {}
    
    public record UpdateRequest(
        @NotNull BigDecimal minStockQty,
        BigDecimal maxStockQty,
        @NotNull BigDecimal reorderLevelQty,
        @NotNull BigDecimal reorderQty,
        @NotNull Boolean allowNegativeStock,
        @NotBlank @Size(max = 20) String valuationMethod,
        UUID defaultLocationId,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, UUID itemId, String itemCode, String itemName,
        UUID storeId, String storeCode, String storeName,
        BigDecimal minStockQty, BigDecimal maxStockQty,
        BigDecimal reorderLevelQty, BigDecimal reorderQty,
        boolean allowNegativeStock, String valuationMethod,
        UUID defaultLocationId,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
}
