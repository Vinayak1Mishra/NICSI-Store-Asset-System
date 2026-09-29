package com.nicsi.store.workflow.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "step_definition", schema = "workflow")
public class WorkflowStepDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "step_code", nullable = false, length = 60)
    private String stepCode;

    @Column(name = "step_name", nullable = false, length = 150)
    private String stepName;

    @Column(name = "approver_type", nullable = false, length = 30)
    private String approverType;

    @Column(name = "approver_role_code", length = 80)
    private String approverRoleCode;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "rule_expression", columnDefinition = "jsonb")
    private String ruleExpression;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "allow_reject", nullable = false)
    private boolean allowReject = true;

    @Column(name = "allow_return", nullable = false)
    private boolean allowReturn = true;

    public WorkflowStepDefinition() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public WorkflowDefinition getWorkflowDefinition() { return workflowDefinition; }
    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) { this.workflowDefinition = workflowDefinition; }

    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }

    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getApproverType() { return approverType; }
    public void setApproverType(String approverType) { this.approverType = approverType; }

    public String getApproverRoleCode() { return approverRoleCode; }
    public void setApproverRoleCode(String approverRoleCode) { this.approverRoleCode = approverRoleCode; }

    public String getRuleExpression() { return ruleExpression; }
    public void setRuleExpression(String ruleExpression) { this.ruleExpression = ruleExpression; }

    public Integer getSlaHours() { return slaHours; }
    public void setSlaHours(Integer slaHours) { this.slaHours = slaHours; }

    public boolean isAllowReject() { return allowReject; }
    public void setAllowReject(boolean allowReject) { this.allowReject = allowReject; }

    public boolean isAllowReturn() { return allowReturn; }
    public void setAllowReturn(boolean allowReturn) { this.allowReturn = allowReturn; }
}
