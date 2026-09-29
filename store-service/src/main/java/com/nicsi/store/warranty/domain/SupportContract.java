package com.nicsi.store.warranty.domain;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.master.domain.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "support_contract", schema = "store")
public class SupportContract {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "contract_type", nullable = false, length = 20)
    private String contractType; // WARRANTY, AMC, CMC, SUPPORT, SUBSCRIPTION

    @Column(name = "contract_number", length = 100)
    private String contractNumber;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "vendor_name_snapshot", length = 250)
    private String vendorNameSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "coverage_detail", columnDefinition = "text")
    private String coverageDetail;

    @Column(name = "sla_detail", columnDefinition = "text")
    private String slaDetail;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "renewal_reminder_days", nullable = false)
    private Integer renewalReminderDays = 30;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(name = "updated_by")
    private UUID updatedBy;

    @PreUpdate public void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getContractType() { return contractType; }
    public void setContractType(String v) { this.contractType = v; }
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String v) { this.contractNumber = v; }
    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID v) { this.vendorId = v; }
    public String getVendorNameSnapshot() { return vendorNameSnapshot; }
    public void setVendorNameSnapshot(String v) { this.vendorNameSnapshot = v; }
    public Item getItem() { return item; }
    public void setItem(Item v) { this.item = v; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset v) { this.asset = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public String getCoverageDetail() { return coverageDetail; }
    public void setCoverageDetail(String v) { this.coverageDetail = v; }
    public String getSlaDetail() { return slaDetail; }
    public void setSlaDetail(String v) { this.slaDetail = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public Integer getRenewalReminderDays() { return renewalReminderDays; }
    public void setRenewalReminderDays(Integer v) { this.renewalReminderDays = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID v) { this.updatedBy = v; }
}
