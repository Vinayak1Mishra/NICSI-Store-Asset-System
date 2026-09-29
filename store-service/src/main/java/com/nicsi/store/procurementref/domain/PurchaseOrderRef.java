package com.nicsi.store.procurementref.domain;

import com.nicsi.store.master.domain.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_ref", schema = "store")
public class PurchaseOrderRef {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "source_system", nullable = false, length = 30)
    private String sourceSystem = "NICSI_ERP";

    @Column(name = "source_po_id")
    private UUID sourcePoId;

    @Column(name = "po_number", nullable = false, length = 80)
    private String poNumber;

    @Column(name = "po_date")
    private LocalDate poDate;

    @Column(name = "procurement_mode", length = 30)
    private String procurementMode;

    @Column(name = "gem_order_number", length = 100)
    private String gemOrderNumber;

    @Column(name = "contract_number", length = 100)
    private String contractNumber;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "vendor_code_snapshot", length = 60)
    private String vendorCodeSnapshot;

    @Column(name = "vendor_name_snapshot", length = 250)
    private String vendorNameSnapshot;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "raw_snapshot", columnDefinition = "jsonb")
    private String rawSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrderRef", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("poLineNo ASC")
    private List<PurchaseOrderItemRef> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (currencyCode == null) currencyCode = "INR";
        if (sourceSystem == null) sourceSystem = "NICSI_ERP";
        if (status == null) status = "OPEN";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public PurchaseOrderRef() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    public UUID getSourcePoId() { return sourcePoId; }
    public void setSourcePoId(UUID sourcePoId) { this.sourcePoId = sourcePoId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public LocalDate getPoDate() { return poDate; }
    public void setPoDate(LocalDate poDate) { this.poDate = poDate; }

    public String getProcurementMode() { return procurementMode; }
    public void setProcurementMode(String procurementMode) { this.procurementMode = procurementMode; }

    public String getGemOrderNumber() { return gemOrderNumber; }
    public void setGemOrderNumber(String gemOrderNumber) { this.gemOrderNumber = gemOrderNumber; }

    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public String getVendorCodeSnapshot() { return vendorCodeSnapshot; }
    public void setVendorCodeSnapshot(String vendorCodeSnapshot) { this.vendorCodeSnapshot = vendorCodeSnapshot; }

    public String getVendorNameSnapshot() { return vendorNameSnapshot; }
    public void setVendorNameSnapshot(String vendorNameSnapshot) { this.vendorNameSnapshot = vendorNameSnapshot; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRawSnapshot() { return rawSnapshot; }
    public void setRawSnapshot(String rawSnapshot) { this.rawSnapshot = rawSnapshot; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<PurchaseOrderItemRef> getItems() { return items; }
    public void setItems(List<PurchaseOrderItemRef> items) { this.items = items; }

    public void addItem(PurchaseOrderItemRef item) {
        items.add(item);
        item.setPurchaseOrderRef(this);
    }
}
