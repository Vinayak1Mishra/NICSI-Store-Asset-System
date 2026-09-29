package com.nicsi.store.grn.domain;

import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.procurementref.domain.PurchaseOrderItemRef;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "grn_item", schema = "store")
public class GrnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_id", nullable = false)
    private Grn grn;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_item_ref_id")
    private PurchaseOrderItemRef purchaseOrderItemRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty;

    @Column(name = "accepted_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal acceptedQty = BigDecimal.ZERO;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "unit_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitRate = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_location_id", nullable = false)
    private StorageLocation receivingLocation;

    @Column(name = "batch_lot_no", length = 120)
    private String batchLotNo;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(columnDefinition = "text")
    private String remarks;

    @PrePersist
    protected void onCreate() {
        if (acceptedQty == null) acceptedQty = BigDecimal.ZERO;
        if (rejectedQty == null) rejectedQty = BigDecimal.ZERO;
        if (unitRate == null) unitRate = BigDecimal.ZERO;
    }

    public GrnItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Grn getGrn() { return grn; }
    public void setGrn(Grn grn) { this.grn = grn; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public PurchaseOrderItemRef getPurchaseOrderItemRef() { return purchaseOrderItemRef; }
    public void setPurchaseOrderItemRef(PurchaseOrderItemRef purchaseOrderItemRef) { this.purchaseOrderItemRef = purchaseOrderItemRef; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }

    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }

    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }

    public BigDecimal getUnitRate() { return unitRate; }
    public void setUnitRate(BigDecimal unitRate) { this.unitRate = unitRate; }

    public StorageLocation getReceivingLocation() { return receivingLocation; }
    public void setReceivingLocation(StorageLocation receivingLocation) { this.receivingLocation = receivingLocation; }

    public String getBatchLotNo() { return batchLotNo; }
    public void setBatchLotNo(String batchLotNo) { this.batchLotNo = batchLotNo; }

    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
