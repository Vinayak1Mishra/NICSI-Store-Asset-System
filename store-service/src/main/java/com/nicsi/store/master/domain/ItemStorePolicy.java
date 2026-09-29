package com.nicsi.store.master.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "item_store_policy", schema = "store")
public class ItemStorePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @Column(name = "min_stock_qty", precision = 18, scale = 3, nullable = false)
    private BigDecimal minStockQty = BigDecimal.ZERO;

    @Column(name = "max_stock_qty", precision = 18, scale = 3)
    private BigDecimal maxStockQty;

    @Column(name = "reorder_level_qty", precision = 18, scale = 3, nullable = false)
    private BigDecimal reorderLevelQty = BigDecimal.ZERO;

    @Column(name = "reorder_qty", precision = 18, scale = 3, nullable = false)
    private BigDecimal reorderQty = BigDecimal.ZERO;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock = false;

    @Column(name = "valuation_method", length = 20, nullable = false)
    private String valuationMethod = "WEIGHTED_AVG";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_location_id")
    private StorageLocation defaultLocation;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public StoreSite getStore() { return store; }
    public void setStore(StoreSite store) { this.store = store; }
    public BigDecimal getMinStockQty() { return minStockQty; }
    public void setMinStockQty(BigDecimal minStockQty) { this.minStockQty = minStockQty; }
    public BigDecimal getMaxStockQty() { return maxStockQty; }
    public void setMaxStockQty(BigDecimal maxStockQty) { this.maxStockQty = maxStockQty; }
    public BigDecimal getReorderLevelQty() { return reorderLevelQty; }
    public void setReorderLevelQty(BigDecimal reorderLevelQty) { this.reorderLevelQty = reorderLevelQty; }
    public BigDecimal getReorderQty() { return reorderQty; }
    public void setReorderQty(BigDecimal reorderQty) { this.reorderQty = reorderQty; }
    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }
    public String getValuationMethod() { return valuationMethod; }
    public void setValuationMethod(String valuationMethod) { this.valuationMethod = valuationMethod; }
    public StorageLocation getDefaultLocation() { return defaultLocation; }
    public void setDefaultLocation(StorageLocation defaultLocation) { this.defaultLocation = defaultLocation; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
