package com.nicsi.store.workflow.repository;

import com.nicsi.store.workflow.domain.WorkflowInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    Optional<WorkflowInstance> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    Page<WorkflowInstance> findByStatus(String status, Pageable pageable);
}
