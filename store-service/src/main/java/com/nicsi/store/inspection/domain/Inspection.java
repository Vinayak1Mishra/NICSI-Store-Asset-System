package com.nicsi.store.inspection.domain;

import com.nicsi.store.grn.domain.Grn;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inspection", schema = "store")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "inspection_no", nullable = false, unique = true, length = 60)
    private String inspectionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_id", nullable = false)
    private Grn grn;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate = LocalDate.now();

    @Column(name = "inspected_by_user_id", nullable = false)
    private UUID inspectedByUserId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "overall_remarks", columnDefinition = "text")
    private String overallRemarks;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

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

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InspectionItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (inspectionDate == null) inspectionDate = LocalDate.now();
        if (status == null) status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Inspection() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getInspectionNo() { return inspectionNo; }
    public void setInspectionNo(String inspectionNo) { this.inspectionNo = inspectionNo; }

    public Grn getGrn() { return grn; }
    public void setGrn(Grn grn) { this.grn = grn; }

    public LocalDate getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(LocalDate inspectionDate) { this.inspectionDate = inspectionDate; }

    public UUID getInspectedByUserId() { return inspectedByUserId; }
    public void setInspectedByUserId(UUID inspectedByUserId) { this.inspectedByUserId = inspectedByUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOverallRemarks() { return overallRemarks; }
    public void setOverallRemarks(String overallRemarks) { this.overallRemarks = overallRemarks; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

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

    public List<InspectionItem> getItems() { return items; }
    public void setItems(List<InspectionItem> items) { this.items = items; }

    public void addItem(InspectionItem item) {
        items.add(item);
        item.setInspection(this);
    }
}
