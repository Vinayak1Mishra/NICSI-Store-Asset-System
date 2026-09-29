package com.nicsi.store.asset.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "repair_ticket", schema = "store")
public class RepairTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "repair_no", nullable = false, unique = true, length = 60)
    private String repairNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "complaint_date", nullable = false)
    private LocalDate complaintDate = LocalDate.now();

    @Column(name = "complaint_detail", nullable = false, columnDefinition = "text")
    private String complaintDetail;

    @Column(name = "warranty_claim", nullable = false)
    private Boolean warrantyClaim = false;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(name = "vendor_name_snapshot", length = 250)
    private String vendorNameSnapshot;

    @Column(name = "dispatch_challan_no", length = 100)
    private String dispatchChallanNo;

    @Column(name = "sent_date")
    private LocalDate sentDate;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(columnDefinition = "text")
    private String diagnosis;

    @Column(name = "repair_action", columnDefinition = "text")
    private String repairAction;

    @Column(name = "parts_replaced", columnDefinition = "text")
    private String partsReplaced;

    @Column(name = "repair_cost", precision = 18, scale = 2, nullable = false)
    private BigDecimal repairCost = BigDecimal.ZERO;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "final_condition", length = 30)
    private String finalCondition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public RepairTicket() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRepairNo() { return repairNo; }
    public void setRepairNo(String repairNo) { this.repairNo = repairNo; }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }

    public LocalDate getComplaintDate() { return complaintDate; }
    public void setComplaintDate(LocalDate complaintDate) { this.complaintDate = complaintDate; }

    public String getComplaintDetail() { return complaintDetail; }
    public void setComplaintDetail(String complaintDetail) { this.complaintDetail = complaintDetail; }

    public Boolean getWarrantyClaim() { return warrantyClaim; }
    public void setWarrantyClaim(Boolean warrantyClaim) { this.warrantyClaim = warrantyClaim; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public String getVendorNameSnapshot() { return vendorNameSnapshot; }
    public void setVendorNameSnapshot(String vendorNameSnapshot) { this.vendorNameSnapshot = vendorNameSnapshot; }

    public String getDispatchChallanNo() { return dispatchChallanNo; }
    public void setDispatchChallanNo(String dispatchChallanNo) { this.dispatchChallanNo = dispatchChallanNo; }

    public LocalDate getSentDate() { return sentDate; }
    public void setSentDate(LocalDate sentDate) { this.sentDate = sentDate; }

    public LocalDate getExpectedReturnDate() { return expectedReturnDate; }
    public void setExpectedReturnDate(LocalDate expectedReturnDate) { this.expectedReturnDate = expectedReturnDate; }

    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getRepairAction() { return repairAction; }
    public void setRepairAction(String repairAction) { this.repairAction = repairAction; }

    public String getPartsReplaced() { return partsReplaced; }
    public void setPartsReplaced(String partsReplaced) { this.partsReplaced = partsReplaced; }

    public BigDecimal getRepairCost() { return repairCost; }
    public void setRepairCost(BigDecimal repairCost) { this.repairCost = repairCost; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFinalCondition() { return finalCondition; }
    public void setFinalCondition(String finalCondition) { this.finalCondition = finalCondition; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
