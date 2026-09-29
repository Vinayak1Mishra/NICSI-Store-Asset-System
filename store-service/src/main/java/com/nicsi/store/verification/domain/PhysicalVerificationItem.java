package com.nicsi.store.verification.domain;

import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "physical_verification_item", schema = "store")
public class PhysicalVerificationItem {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false)
    private PhysicalVerification verification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "asset_id")
    private UUID assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "book_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal bookQty = BigDecimal.ZERO;

    @Column(name = "physical_qty", precision = 18, scale = 3)
    private BigDecimal physicalQty;

    @Column(name = "variance_qty", precision = 18, scale = 3)
    private BigDecimal varianceQty;

    @Column(name = "result_status", length = 30)
    private String resultStatus;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Column(name = "condition_status", length = 30)
    private String conditionStatus;

    @Column(columnDefinition = "text")
    private String remarks;

    public void setVerification(PhysicalVerification v) { this.verification = v; }
    public UUID getId() { return id; }
    public PhysicalVerification getVerification() { return verification; }
    public Item getItem() { return item; }
    public void setItem(Item v) { this.item = v; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID v) { this.assetId = v; }
    public StorageLocation getLocation() { return location; }
    public void setLocation(StorageLocation v) { this.location = v; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }
    public BigDecimal getBookQty() { return bookQty; }
    public void setBookQty(BigDecimal v) { this.bookQty = v; }
    public BigDecimal getPhysicalQty() { return physicalQty; }
    public void setPhysicalQty(BigDecimal v) { this.physicalQty = v; }
    public BigDecimal getVarianceQty() { return varianceQty; }
    public void setVarianceQty(BigDecimal v) { this.varianceQty = v; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String v) { this.resultStatus = v; }
    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant v) { this.scannedAt = v; }
    public UUID getScannedBy() { return scannedBy; }
    public void setScannedBy(UUID v) { this.scannedBy = v; }
    public String getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(String v) { this.conditionStatus = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
}
