package com.nicsi.store.inventory.domain;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "stock_adjustment_item",
    schema = "store",
    uniqueConstraints = @UniqueConstraint(name = "uq_adj_line", columnNames = {"adjustment_id", "line_no"})
)
public class StockAdjustmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private StockAdjustment adjustment;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(nullable = false, length = 3)
    private String direction;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(columnDefinition = "text")
    private String remarks;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StockAdjustment getAdjustment() {
        return adjustment;
    }

    public void setAdjustment(StockAdjustment adjustment) {
        this.adjustment = adjustment;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
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

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
