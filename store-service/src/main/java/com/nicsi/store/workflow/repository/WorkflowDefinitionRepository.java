package com.nicsi.store.workflow.repository;

import com.nicsi.store.workflow.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {
    Optional<WorkflowDefinition> findByEntityTypeAndActiveTrue(String entityType);
    Optional<WorkflowDefinition> findByWorkflowCodeAndActiveTrue(String workflowCode);
}
