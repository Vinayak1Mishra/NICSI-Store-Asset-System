package com.nicsi.store.inspection.domain;

import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.master.domain.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inspection_item", schema = "store")
public class InspectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_item_id", nullable = false)
    private GrnItem grnItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "inspected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal inspectedQty;

    @Column(name = "accepted_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal acceptedQty = BigDecimal.ZERO;

    @Column(name = "rejected_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal rejectedQty = BigDecimal.ZERO;

    @Column(name = "quarantine_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal quarantineQty = BigDecimal.ZERO;

    @Column(name = "specification_match")
    private Boolean specificationMatch;

    @Column(name = "physical_condition", length = 20)
    private String physicalCondition;

    @Column(name = "warranty_verified")
    private Boolean warrantyVerified;

    @Column(name = "accessory_verified")
    private Boolean accessoryVerified;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "technical_result", columnDefinition = "jsonb")
    private String technicalResult;

    @Column(columnDefinition = "text")
    private String remarks;

    @PrePersist
    protected void onCreate() {
        if (acceptedQty == null) acceptedQty = BigDecimal.ZERO;
        if (rejectedQty == null) rejectedQty = BigDecimal.ZERO;
        if (quarantineQty == null) quarantineQty = BigDecimal.ZERO;
    }

    public InspectionItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Inspection getInspection() { return inspection; }
    public void setInspection(Inspection inspection) { this.inspection = inspection; }

    public GrnItem getGrnItem() { return grnItem; }
    public void setGrnItem(GrnItem grnItem) { this.grnItem = grnItem; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public BigDecimal getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(BigDecimal inspectedQty) { this.inspectedQty = inspectedQty; }

    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }

    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }

    public BigDecimal getQuarantineQty() { return quarantineQty; }
    public void setQuarantineQty(BigDecimal quarantineQty) { this.quarantineQty = quarantineQty; }

    public Boolean getSpecificationMatch() { return specificationMatch; }
    public void setSpecificationMatch(Boolean specificationMatch) { this.specificationMatch = specificationMatch; }

    public String getPhysicalCondition() { return physicalCondition; }
    public void setPhysicalCondition(String physicalCondition) { this.physicalCondition = physicalCondition; }

    public Boolean getWarrantyVerified() { return warrantyVerified; }
    public void setWarrantyVerified(Boolean warrantyVerified) { this.warrantyVerified = warrantyVerified; }

    public Boolean getAccessoryVerified() { return accessoryVerified; }
    public void setAccessoryVerified(Boolean accessoryVerified) { this.accessoryVerified = accessoryVerified; }

    public String getTechnicalResult() { return technicalResult; }
    public void setTechnicalResult(String technicalResult) { this.technicalResult = technicalResult; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
