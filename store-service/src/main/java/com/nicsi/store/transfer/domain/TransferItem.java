package com.nicsi.store.transfer.domain;

import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transfer_item", schema = "store")
public class TransferItem {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private TransferHeader transferHeader;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "lot_id")
    private UUID lotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id", nullable = false)
    private StorageLocation sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private StorageLocation destinationLocation;

    @Column(name = "transfer_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal transferQty;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String remarks;

    public void setTransferHeader(TransferHeader h) { this.transferHeader = h; }
    public UUID getId() { return id; }
    public TransferHeader getTransferHeader() { return transferHeader; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer v) { this.lineNo = v; }
    public Item getItem() { return item; }
    public void setItem(Item v) { this.item = v; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID v) { this.assetId = v; }
    public UUID getLotId() { return lotId; }
    public void setLotId(UUID v) { this.lotId = v; }
    public StorageLocation getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(StorageLocation v) { this.sourceLocation = v; }
    public StorageLocation getDestinationLocation() { return destinationLocation; }
    public void setDestinationLocation(StorageLocation v) { this.destinationLocation = v; }
    public BigDecimal getTransferQty() { return transferQty; }
    public void setTransferQty(BigDecimal v) { this.transferQty = v; }
    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal v) { this.receivedQty = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
}
