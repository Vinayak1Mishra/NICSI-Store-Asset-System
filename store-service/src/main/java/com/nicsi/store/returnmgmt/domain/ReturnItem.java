package com.nicsi.store.returnmgmt.domain;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Return line item — one returned material/asset.
 * Maps to store.return_item.
 */
@Entity
@Table(name = "return_item", schema = "store")
public class ReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private ReturnHeader returnHeader;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "return_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal returnQty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_location_id", nullable = false)
    private StorageLocation returnLocation;

    /** GOOD | FAIR | DAMAGED | UNSERVICEABLE */
    @Column(name = "condition_status", length = 30)
    private String conditionStatus;

    /** RESTOCK | REPAIR | QUARANTINE | CONDEMNATION | SCRAP */
    @Column(name = "disposition", length = 30)
    private String disposition;

    @Column(columnDefinition = "text")
    private String remarks;

    public ReturnItem() {}

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReturnHeader getReturnHeader() {
        return returnHeader;
    }

    public void setReturnHeader(ReturnHeader returnHeader) {
        this.returnHeader = returnHeader;
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

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public BigDecimal getReturnQty() {
        return returnQty;
    }

    public void setReturnQty(BigDecimal returnQty) {
        this.returnQty = returnQty;
    }

    public StorageLocation getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(StorageLocation returnLocation) {
        this.returnLocation = returnLocation;
    }

    public String getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(String conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
