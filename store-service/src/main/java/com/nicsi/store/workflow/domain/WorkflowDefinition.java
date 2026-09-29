package com.nicsi.store.workflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "definition", schema = "workflow")
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "workflow_code", nullable = false, length = 60)
    private String workflowCode;

    @Column(name = "workflow_name", nullable = false, length = 150)
    private String workflowName;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "workflowDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo ASC")
    private List<WorkflowStepDefinition> steps = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public WorkflowDefinition() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getWorkflowCode() { return workflowCode; }
    public void setWorkflowCode(String workflowCode) { this.workflowCode = workflowCode; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public List<WorkflowStepDefinition> getSteps() { return steps; }
    public void setSteps(List<WorkflowStepDefinition> steps) { this.steps = steps; }
}
