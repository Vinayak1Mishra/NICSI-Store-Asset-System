package com.nicsi.store.workflow.repository;

import com.nicsi.store.workflow.domain.WorkflowAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowActionRepository extends JpaRepository<WorkflowAction, UUID> {
    List<WorkflowAction> findByWorkflowInstanceIdOrderByActionTimeAsc(UUID workflowInstanceId);
}
