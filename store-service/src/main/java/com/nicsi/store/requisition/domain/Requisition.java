package com.nicsi.store.requisition.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "requisition", schema = "store")
public class Requisition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "requisition_no", nullable = false, unique = true, length = 60)
    private String requisitionNo;

    @Column(name = "requisition_date", nullable = false)
    private LocalDate requisitionDate = LocalDate.now();

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "requester_name_snapshot", length = 200)
    private String requesterNameSnapshot;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "department_code_snapshot", length = 50)
    private String departmentCodeSnapshot;

    @Column(name = "department_name_snapshot", length = 200)
    private String departmentNameSnapshot;

    @Column(name = "division_id")
    private UUID divisionId;

    @Column(name = "division_name_snapshot", length = 200)
    private String divisionNameSnapshot;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "project_code_snapshot", length = 60)
    private String projectCodeSnapshot;

    @Column(name = "project_name_snapshot", length = 250)
    private String projectNameSnapshot;

    @Column(columnDefinition = "text")
    private String purpose;

    @Column(nullable = false, length = 15)
    private String priority = "NORMAL";

    @Column(name = "required_by_date")
    private LocalDate requiredByDate;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "total_estimated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalEstimatedAmount = BigDecimal.ZERO;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

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

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<RequisitionItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (requisitionDate == null) requisitionDate = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Requisition() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRequisitionNo() { return requisitionNo; }
    public void setRequisitionNo(String requisitionNo) { this.requisitionNo = requisitionNo; }

    public LocalDate getRequisitionDate() { return requisitionDate; }
    public void setRequisitionDate(LocalDate requisitionDate) { this.requisitionDate = requisitionDate; }

    public UUID getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(UUID requesterUserId) { this.requesterUserId = requesterUserId; }

    public String getRequesterNameSnapshot() { return requesterNameSnapshot; }
    public void setRequesterNameSnapshot(String requesterNameSnapshot) { this.requesterNameSnapshot = requesterNameSnapshot; }

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }

    public String getDepartmentCodeSnapshot() { return departmentCodeSnapshot; }
    public void setDepartmentCodeSnapshot(String departmentCodeSnapshot) { this.departmentCodeSnapshot = departmentCodeSnapshot; }

    public String getDepartmentNameSnapshot() { return departmentNameSnapshot; }
    public void setDepartmentNameSnapshot(String departmentNameSnapshot) { this.departmentNameSnapshot = departmentNameSnapshot; }

    public UUID getDivisionId() { return divisionId; }
    public void setDivisionId(UUID divisionId) { this.divisionId = divisionId; }

    public String getDivisionNameSnapshot() { return divisionNameSnapshot; }
    public void setDivisionNameSnapshot(String divisionNameSnapshot) { this.divisionNameSnapshot = divisionNameSnapshot; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProjectCodeSnapshot() { return projectCodeSnapshot; }
    public void setProjectCodeSnapshot(String projectCodeSnapshot) { this.projectCodeSnapshot = projectCodeSnapshot; }

    public String getProjectNameSnapshot() { return projectNameSnapshot; }
    public void setProjectNameSnapshot(String projectNameSnapshot) { this.projectNameSnapshot = projectNameSnapshot; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getRequiredByDate() { return requiredByDate; }
    public void setRequiredByDate(LocalDate requiredByDate) { this.requiredByDate = requiredByDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalEstimatedAmount() { return totalEstimatedAmount; }
    public void setTotalEstimatedAmount(BigDecimal totalEstimatedAmount) { this.totalEstimatedAmount = totalEstimatedAmount; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

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

    public List<RequisitionItem> getItems() { return items; }
    public void setItems(List<RequisitionItem> items) { this.items = items; }
}
