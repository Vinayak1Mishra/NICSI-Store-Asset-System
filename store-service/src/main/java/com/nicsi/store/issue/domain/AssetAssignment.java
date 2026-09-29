package com.nicsi.store.issue.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Asset assignment history record — records the custodianship of an asset
 * at every issue/return/transfer. Maps to store.asset_assignment.
 */
@Entity
@Table(name = "asset_assignment", schema = "store")
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /** EMPLOYEE | DEPARTMENT | PROJECT | LOCATION */
    @Column(name = "assignment_type", nullable = false, length = 20)
    private String assignmentType;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "assignee_name_snapshot", length = 200)
    private String assigneeNameSnapshot;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "issue_id")
    private UUID issueId;

    @Column(name = "assigned_from", nullable = false)
    private Instant assignedFrom = Instant.now();

    @Column(name = "assigned_until")
    private Instant assignedUntil;

    /** ACTIVE | RETURNED | TRANSFERRED | CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "acknowledgement_at")
    private Instant acknowledgementAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public String getAssignmentType() { return assignmentType; }
    public void setAssignmentType(String assignmentType) { this.assignmentType = assignmentType; }

    public UUID getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(UUID assigneeUserId) { this.assigneeUserId = assigneeUserId; }

    public String getAssigneeNameSnapshot() { return assigneeNameSnapshot; }
    public void setAssigneeNameSnapshot(String assigneeNameSnapshot) { this.assigneeNameSnapshot = assigneeNameSnapshot; }

    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public UUID getIssueId() { return issueId; }
    public void setIssueId(UUID issueId) { this.issueId = issueId; }

    public Instant getAssignedFrom() { return assignedFrom; }
    public void setAssignedFrom(Instant assignedFrom) { this.assignedFrom = assignedFrom; }

    public Instant getAssignedUntil() { return assignedUntil; }
    public void setAssignedUntil(Instant assignedUntil) { this.assignedUntil = assignedUntil; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getAcknowledgementAt() { return acknowledgementAt; }
    public void setAcknowledgementAt(Instant acknowledgementAt) { this.acknowledgementAt = acknowledgementAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
