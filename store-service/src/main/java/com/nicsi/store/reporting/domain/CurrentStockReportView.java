package com.nicsi.store.reporting.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "vw_current_stock", schema = "store")
public class CurrentStockReportView {

    @Id
    private UUID stockBalanceId;
    private UUID itemId;
    private String itemCode;
    private String itemName;
    private String itemType;
    private UUID storeId;
    private String storeCode;
    private String storeName;
    private String lotBatchNumber;
    private BigDecimal onHandQty;
    private BigDecimal reservedQty;
    private BigDecimal availableQty;
    private BigDecimal avgUnitCost;
    private BigDecimal totalValue;
    private UUID baseUomId;

    protected CurrentStockReportView() {}

    public UUID getStockBalanceId() { return stockBalanceId; }
    public UUID getItemId() { return itemId; }
    public String getItemCode() { return itemCode; }
    public String getItemName() { return itemName; }
    public String getItemType() { return itemType; }
    public UUID getStoreId() { return storeId; }
    public String getStoreCode() { return storeCode; }
    public String getStoreName() { return storeName; }
    public String getLotBatchNumber() { return lotBatchNumber; }
    public BigDecimal getOnHandQty() { return onHandQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public BigDecimal getAvgUnitCost() { return avgUnitCost; }
    public BigDecimal getTotalValue() { return totalValue; }
    public UUID getBaseUomId() { return baseUomId; }
}
