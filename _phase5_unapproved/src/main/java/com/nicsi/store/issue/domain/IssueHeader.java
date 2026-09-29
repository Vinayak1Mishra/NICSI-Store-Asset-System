package com.nicsi.store.issue.domain;

import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.requisition.domain.Requisition;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Issue header — the controlling document for issuing stock to an employee,
 * department, project or location. Maps to store.issue_header.
 *
 * State flow: DRAFT → SUBMITTED → APPROVED → POSTED → ACKNOWLEDGED
 *             (or REJECTED / CANCELLED from SUBMITTED/APPROVED)
 */
@Entity
@Table(name = "issue_header", schema = "store")
public class IssueHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "issue_no", nullable = false, unique = true, length = 60)
    private String issueNo;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id")
    private Requisition requisition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    /** EMPLOYEE | DEPARTMENT | PROJECT | LOCATION | OTHER */
    @Column(name = "issued_to_type", nullable = false, length = 20)
    private String issuedToType;

    @Column(name = "issued_to_user_id")
    private UUID issuedToUserId;

    @Column(name = "issued_to_name_snapshot", length = 200)
    private String issuedToNameSnapshot;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "department_name_snapshot", length = 200)
    private String departmentNameSnapshot;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "project_name_snapshot", length = 250)
    private String projectNameSnapshot;

    @Column(columnDefinition = "text")
    private String purpose;

    /**
     * DRAFT | SUBMITTED | APPROVED | POSTED | PARTIALLY_ACKNOWLEDGED | ACKNOWLEDGED | REJECTED | CANCELLED
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "issued_by_user_id")
    private UUID issuedByUserId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledgement_status", length = 30)
    private String acknowledgementStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version = 0L;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    private List<IssueItem> items = new ArrayList<>();

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public Requisition getRequisition() { return requisition; }
    public void setRequisition(Requisition requisition) { this.requisition = requisition; }

    public StoreSite getStore() { return store; }
    public void setStore(StoreSite store) { this.store = store; }

    public String getIssuedToType() { return issuedToType; }
    public void setIssuedToType(String issuedToType) { this.issuedToType = issuedToType; }

    public UUID getIssuedToUserId() { return issuedToUserId; }
    public void setIssuedToUserId(UUID issuedToUserId) { this.issuedToUserId = issuedToUserId; }

    public String getIssuedToNameSnapshot() { return issuedToNameSnapshot; }
    public void setIssuedToNameSnapshot(String issuedToNameSnapshot) { this.issuedToNameSnapshot = issuedToNameSnapshot; }

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }

    public String getDepartmentNameSnapshot() { return departmentNameSnapshot; }
    public void setDepartmentNameSnapshot(String departmentNameSnapshot) { this.departmentNameSnapshot = departmentNameSnapshot; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProjectNameSnapshot() { return projectNameSnapshot; }
    public void setProjectNameSnapshot(String projectNameSnapshot) { this.projectNameSnapshot = projectNameSnapshot; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getIssuedByUserId() { return issuedByUserId; }
    public void setIssuedByUserId(UUID issuedByUserId) { this.issuedByUserId = issuedByUserId; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(UUID acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public String getAcknowledgementStatus() { return acknowledgementStatus; }
    public void setAcknowledgementStatus(String acknowledgementStatus) { this.acknowledgementStatus = acknowledgementStatus; }

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

    public List<IssueItem> getItems() { return items; }
    public void setItems(List<IssueItem> items) { this.items = items; }
}
