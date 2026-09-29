package com.nicsi.store.inventory.domain;

import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_balance", schema = "store")
public class StockBalance {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(name = "on_hand_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal onHandQty = BigDecimal.ZERO;

    @Column(name = "reserved_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQty = BigDecimal.ZERO;

    @Column(name = "available_qty", insertable = false, updatable = false, precision = 18, scale = 3)
    private BigDecimal availableQty;

    @Column(name = "avg_unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgUnitCost = BigDecimal.ZERO;

    @Column(name = "inventory_value", insertable = false, updatable = false, precision = 20, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    @PreUpdate
    protected void onPersistOrUpdate() {
        this.updatedAt = Instant.now();
        if (this.onHandQty == null) this.onHandQty = BigDecimal.ZERO;
        if (this.reservedQty == null) this.reservedQty = BigDecimal.ZERO;
        if (this.avgUnitCost == null) this.avgUnitCost = BigDecimal.ZERO;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public StoreSite getStore() {
        return store;
    }

    public void setStore(StoreSite store) {
        this.store = store;
    }

    public StorageLocation getLocation() {
        return location;
    }

    public void setLocation(StorageLocation location) {
        this.location = location;
    }

    public InventoryLot getLot() {
        return lot;
    }

    public void setLot(InventoryLot lot) {
        this.lot = lot;
    }

    public BigDecimal getOnHandQty() {
        return onHandQty;
    }

    public void setOnHandQty(BigDecimal onHandQty) {
        this.onHandQty = onHandQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(BigDecimal reservedQty) {
        this.reservedQty = reservedQty;
    }

    public BigDecimal getAvailableQty() {
        return availableQty != null ? availableQty : (onHandQty != null && reservedQty != null ? onHandQty.subtract(reservedQty) : BigDecimal.ZERO);
    }

    public void setAvailableQty(BigDecimal availableQty) {
        this.availableQty = availableQty;
    }

    public BigDecimal getAvgUnitCost() {
        return avgUnitCost;
    }

    public void setAvgUnitCost(BigDecimal avgUnitCost) {
        this.avgUnitCost = avgUnitCost;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue != null ? inventoryValue : (onHandQty != null && avgUnitCost != null ? onHandQty.multiply(avgUnitCost).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
    }

    public void setInventoryValue(BigDecimal inventoryValue) {
        this.inventoryValue = inventoryValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
