package com.nicsi.store.workflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "instance", schema = "workflow")
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "current_step_no")
    private Integer currentStepNo;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "started_by", nullable = false, updatable = false)
    private UUID startedBy;

    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("actionTime ASC")
    private List<WorkflowAction> actions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = Instant.now();
    }

    public WorkflowInstance() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WorkflowDefinition getWorkflowDefinition() { return workflowDefinition; }
    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) { this.workflowDefinition = workflowDefinition; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getCurrentStepNo() { return currentStepNo; }
    public void setCurrentStepNo(Integer currentStepNo) { this.currentStepNo = currentStepNo; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public UUID getStartedBy() { return startedBy; }
    public void setStartedBy(UUID startedBy) { this.startedBy = startedBy; }

    public List<WorkflowAction> getActions() { return actions; }
    public void setActions(List<WorkflowAction> actions) { this.actions = actions; }
}
