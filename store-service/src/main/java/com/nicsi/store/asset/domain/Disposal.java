package com.nicsi.store.asset.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "disposal", schema = "store")
public class Disposal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "disposal_no", nullable = false, unique = true, length = 60)
    private String disposalNo;

    @Column(name = "disposal_date")
    private LocalDate disposalDate = LocalDate.now();

    @Column(name = "disposal_method", nullable = false, length = 30)
    private String disposalMethod;

    @Column(name = "purchaser_vendor_id")
    private UUID purchaserVendorId;

    @Column(name = "purchaser_name_snapshot", length = 250)
    private String purchaserNameSnapshot;

    @Column(name = "sale_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal saleAmount = BigDecimal.ZERO;

    @Column(name = "certificate_number", length = 120)
    private String certificateNumber;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @OneToMany(mappedBy = "disposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DisposalItem> items = new ArrayList<>();

    public Disposal() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDisposalNo() { return disposalNo; }
    public void setDisposalNo(String disposalNo) { this.disposalNo = disposalNo; }

    public LocalDate getDisposalDate() { return disposalDate; }
    public void setDisposalDate(LocalDate disposalDate) { this.disposalDate = disposalDate; }

    public String getDisposalMethod() { return disposalMethod; }
    public void setDisposalMethod(String disposalMethod) { this.disposalMethod = disposalMethod; }

    public UUID getPurchaserVendorId() { return purchaserVendorId; }
    public void setPurchaserVendorId(UUID purchaserVendorId) { this.purchaserVendorId = purchaserVendorId; }

    public String getPurchaserNameSnapshot() { return purchaserNameSnapshot; }
    public void setPurchaserNameSnapshot(String purchaserNameSnapshot) { this.purchaserNameSnapshot = purchaserNameSnapshot; }

    public BigDecimal getSaleAmount() { return saleAmount; }
    public void setSaleAmount(BigDecimal saleAmount) { this.saleAmount = saleAmount; }

    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String certificateNumber) { this.certificateNumber = certificateNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public UUID getPostedBy() { return postedBy; }
    public void setPostedBy(UUID postedBy) { this.postedBy = postedBy; }

    public List<DisposalItem> getItems() { return items; }
    public void setItems(List<DisposalItem> items) { this.items = items; }

    public void addItem(DisposalItem item) {
        items.add(item);
        item.setDisposal(this);
    }
}
