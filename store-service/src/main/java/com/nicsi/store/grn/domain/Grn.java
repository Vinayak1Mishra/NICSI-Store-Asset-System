package com.nicsi.store.grn.domain;

import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.procurementref.domain.PurchaseOrderRef;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "grn", schema = "store")
public class Grn {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "grn_no", nullable = false, unique = true, length = 60)
    private String grnNo;

    @Column(name = "grn_date", nullable = false)
    private LocalDate grnDate = LocalDate.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_ref_id")
    private PurchaseOrderRef purchaseOrderRef;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "vendor_name_snapshot", length = 250)
    private String vendorNameSnapshot;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "challan_number", length = 100)
    private String challanNumber;

    @Column(name = "challan_date")
    private LocalDate challanDate;

    @Column(name = "received_by_user_id", nullable = false)
    private UUID receivedByUserId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "grn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<GrnItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (grnDate == null) grnDate = LocalDate.now();
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Grn() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getGrnNo() { return grnNo; }
    public void setGrnNo(String grnNo) { this.grnNo = grnNo; }

    public LocalDate getGrnDate() { return grnDate; }
    public void setGrnDate(LocalDate grnDate) { this.grnDate = grnDate; }

    public StoreSite getStore() { return store; }
    public void setStore(StoreSite store) { this.store = store; }

    public PurchaseOrderRef getPurchaseOrderRef() { return purchaseOrderRef; }
    public void setPurchaseOrderRef(PurchaseOrderRef purchaseOrderRef) { this.purchaseOrderRef = purchaseOrderRef; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public String getVendorNameSnapshot() { return vendorNameSnapshot; }
    public void setVendorNameSnapshot(String vendorNameSnapshot) { this.vendorNameSnapshot = vendorNameSnapshot; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public String getChallanNumber() { return challanNumber; }
    public void setChallanNumber(String challanNumber) { this.challanNumber = challanNumber; }

    public LocalDate getChallanDate() { return challanDate; }
    public void setChallanDate(LocalDate challanDate) { this.challanDate = challanDate; }

    public UUID getReceivedByUserId() { return receivedByUserId; }
    public void setReceivedByUserId(UUID receivedByUserId) { this.receivedByUserId = receivedByUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<GrnItem> getItems() { return items; }
    public void setItems(List<GrnItem> items) { this.items = items; }

    public void addItem(GrnItem item) {
        items.add(item);
        item.setGrn(this);
    }
}
