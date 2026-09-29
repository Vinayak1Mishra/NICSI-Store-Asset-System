package com.nicsi.store.workflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "action", schema = "workflow")
public class WorkflowAction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "actor_role_code", length = 80)
    private String actorRoleCode;

    @Column(columnDefinition = "text")
    private String comments;

    @Column(name = "action_time", nullable = false, updatable = false)
    private Instant actionTime;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (actionTime == null) actionTime = Instant.now();
    }

    public WorkflowAction() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public void setWorkflowInstance(WorkflowInstance workflowInstance) { this.workflowInstance = workflowInstance; }

    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public UUID getActorUserId() { return actorUserId; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }

    public String getActorRoleCode() { return actorRoleCode; }
    public void setActorRoleCode(String actorRoleCode) { this.actorRoleCode = actorRoleCode; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public Instant getActionTime() { return actionTime; }
    public void setActionTime(Instant actionTime) { this.actionTime = actionTime; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
