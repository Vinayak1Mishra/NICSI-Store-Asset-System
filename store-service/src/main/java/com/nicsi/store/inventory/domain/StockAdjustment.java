package com.nicsi.store.inventory.domain;

import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment", schema = "store")
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "adjustment_no", nullable = false, unique = true, length = 60)
    private String adjustmentNo;

    @Column(name = "adjustment_date", nullable = false)
    private LocalDate adjustmentDate = LocalDate.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @Column(name = "reason_code", nullable = false, length = 30)
    private String reasonCode;

    @Column(name = "reason_detail", nullable = false, columnDefinition = "text")
    private String reasonDetail;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<StockAdjustmentItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (adjustmentDate == null) adjustmentDate = LocalDate.now();
        if (status == null) status = "DRAFT";
    }

    public void addItem(StockAdjustmentItem item) {
        items.add(item);
        item.setAdjustment(this);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAdjustmentNo() {
        return adjustmentNo;
    }

    public void setAdjustmentNo(String adjustmentNo) {
        this.adjustmentNo = adjustmentNo;
    }

    public LocalDate getAdjustmentDate() {
        return adjustmentDate;
    }

    public void setAdjustmentDate(LocalDate adjustmentDate) {
        this.adjustmentDate = adjustmentDate;
    }

    public StoreSite getStore() {
        return store;
    }

    public void setStore(StoreSite store) {
        this.store = store;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public void setReasonDetail(String reasonDetail) {
        this.reasonDetail = reasonDetail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public UUID getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(UUID postedBy) {
        this.postedBy = postedBy;
    }

    public List<StockAdjustmentItem> getItems() {
        return items;
    }

    public void setItems(List<StockAdjustmentItem> items) {
        this.items = items;
    }
}
