package com.nicsi.store.requisition.domain;

import com.nicsi.store.master.domain.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requisition_item", schema = "store")
public class RequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id", nullable = false)
    private Requisition requisition;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "requested_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal requestedQty;

    @Column(name = "approved_qty", precision = 18, scale = 3)
    private BigDecimal approvedQty;

    @Column(name = "issued_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal issuedQty = BigDecimal.ZERO;

    @Column(name = "estimated_unit_rate", precision = 18, scale = 2)
    private BigDecimal estimatedUnitRate;

    @Column(columnDefinition = "text")
    private String specification;

    @Column(columnDefinition = "text")
    private String justification;

    @Column(name = "preferred_make_model", length = 300)
    private String preferredMakeModel;

    @Column(name = "line_status", nullable = false, length = 30)
    private String lineStatus = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public RequisitionItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Requisition getRequisition() { return requisition; }
    public void setRequisition(Requisition requisition) { this.requisition = requisition; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public BigDecimal getRequestedQty() { return requestedQty; }
    public void setRequestedQty(BigDecimal requestedQty) { this.requestedQty = requestedQty; }

    public BigDecimal getApprovedQty() { return approvedQty; }
    public void setApprovedQty(BigDecimal approvedQty) { this.approvedQty = approvedQty; }

    public BigDecimal getIssuedQty() { return issuedQty; }
    public void setIssuedQty(BigDecimal issuedQty) { this.issuedQty = issuedQty; }

    public BigDecimal getEstimatedUnitRate() { return estimatedUnitRate; }
    public void setEstimatedUnitRate(BigDecimal estimatedUnitRate) { this.estimatedUnitRate = estimatedUnitRate; }

    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public String getPreferredMakeModel() { return preferredMakeModel; }
    public void setPreferredMakeModel(String preferredMakeModel) { this.preferredMakeModel = preferredMakeModel; }

    public String getLineStatus() { return lineStatus; }
    public void setLineStatus(String lineStatus) { this.lineStatus = lineStatus; }

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
