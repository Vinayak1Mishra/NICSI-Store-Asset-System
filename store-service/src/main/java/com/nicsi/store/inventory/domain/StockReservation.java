package com.nicsi.store.inventory.domain;

import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.requisition.domain.RequisitionItem;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation", schema = "store")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "reservation_no", nullable = false, unique = true, length = 60)
    private String reservationNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_item_id", nullable = false)
    private RequisitionItem requisitionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private StorageLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(name = "reserved_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQty;

    @Column(name = "consumed_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal consumedQty = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (consumedQty == null) consumedQty = BigDecimal.ZERO;
        if (status == null) status = "ACTIVE";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public void setReservationNo(String reservationNo) {
        this.reservationNo = reservationNo;
    }

    public RequisitionItem getRequisitionItem() {
        return requisitionItem;
    }

    public void setRequisitionItem(RequisitionItem requisitionItem) {
        this.requisitionItem = requisitionItem;
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

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(BigDecimal reservedQty) {
        this.reservedQty = reservedQty;
    }

    public BigDecimal getConsumedQty() {
        return consumedQty;
    }

    public void setConsumedQty(BigDecimal consumedQty) {
        this.consumedQty = consumedQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public UUID getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(UUID releasedBy) {
        this.releasedBy = releasedBy;
    }
}
