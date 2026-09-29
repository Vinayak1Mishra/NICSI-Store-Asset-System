package com.nicsi.store.workflow.repository;

import com.nicsi.store.workflow.domain.WorkflowStepDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowStepDefinitionRepository extends JpaRepository<WorkflowStepDefinition, UUID> {
    List<WorkflowStepDefinition> findByWorkflowDefinitionIdOrderByStepNoAsc(UUID workflowDefinitionId);
    Optional<WorkflowStepDefinition> findByWorkflowDefinitionIdAndStepNo(UUID workflowDefinitionId, Integer stepNo);
    Optional<WorkflowStepDefinition> findByWorkflowDefinitionIdAndStepCode(UUID workflowDefinitionId, String stepCode);
}
