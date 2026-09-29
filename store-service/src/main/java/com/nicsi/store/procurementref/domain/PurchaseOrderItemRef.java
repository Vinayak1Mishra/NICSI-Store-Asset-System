package com.nicsi.store.procurementref.domain;

import com.nicsi.store.master.domain.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_item_ref", schema = "store")
public class PurchaseOrderItemRef {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_ref_id", nullable = false)
    private PurchaseOrderRef purchaseOrderRef;

    @Column(name = "po_line_no", nullable = false)
    private Integer poLineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(name = "item_description", columnDefinition = "text")
    private String itemDescription;

    @Column(name = "ordered_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal orderedQty;

    @Column(name = "received_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(name = "unit_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "delivery_due_date")
    private LocalDate deliveryDueDate;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "project_code_snapshot", length = 60)
    private String projectCodeSnapshot;

    @Column(name = "project_name_snapshot", length = 250)
    private String projectNameSnapshot;

    @PrePersist
    protected void onCreate() {
        if (receivedQty == null) receivedQty = BigDecimal.ZERO;
        if (unitRate == null) unitRate = BigDecimal.ZERO;
        if (taxAmount == null) taxAmount = BigDecimal.ZERO;
    }

    public PurchaseOrderItemRef() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public PurchaseOrderRef getPurchaseOrderRef() { return purchaseOrderRef; }
    public void setPurchaseOrderRef(PurchaseOrderRef purchaseOrderRef) { this.purchaseOrderRef = purchaseOrderRef; }

    public Integer getPoLineNo() { return poLineNo; }
    public void setPoLineNo(Integer poLineNo) { this.poLineNo = poLineNo; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }

    public BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }

    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }

    public BigDecimal getUnitRate() { return unitRate; }
    public void setUnitRate(BigDecimal unitRate) { this.unitRate = unitRate; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public LocalDate getDeliveryDueDate() { return deliveryDueDate; }
    public void setDeliveryDueDate(LocalDate deliveryDueDate) { this.deliveryDueDate = deliveryDueDate; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProjectCodeSnapshot() { return projectCodeSnapshot; }
    public void setProjectCodeSnapshot(String projectCodeSnapshot) { this.projectCodeSnapshot = projectCodeSnapshot; }

    public String getProjectNameSnapshot() { return projectNameSnapshot; }
    public void setProjectNameSnapshot(String projectNameSnapshot) { this.projectNameSnapshot = projectNameSnapshot; }
}
