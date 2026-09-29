package com.nicsi.store.requisition.repository;

import com.nicsi.store.requisition.domain.Requisition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequisitionRepository extends JpaRepository<Requisition, UUID> {

    Optional<Requisition> findByRequisitionNo(String requisitionNo);

    boolean existsByRequisitionNo(String requisitionNo);

    @Query("SELECT r FROM Requisition r WHERE " +
           "(:search IS NULL OR LOWER(r.requisitionNo) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "  OR LOWER(COALESCE(r.purpose, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "  OR LOWER(COALESCE(r.requesterNameSnapshot, '')) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:priority IS NULL OR r.priority = :priority) " +
           "AND (:requesterUserId IS NULL OR r.requesterUserId = :requesterUserId) " +
           "AND (:departmentId IS NULL OR r.departmentId = :departmentId) " +
           "AND (CAST(:fromDate AS date) IS NULL OR r.requisitionDate >= :fromDate) " +
           "AND (CAST(:toDate AS date) IS NULL OR r.requisitionDate <= :toDate)")
    Page<Requisition> search(
            @Param("search") String search,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("requesterUserId") UUID requesterUserId,
            @Param("departmentId") UUID departmentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("SELECT r FROM Requisition r, WorkflowInstance wi, WorkflowStepDefinition wsd " +
           "WHERE wi.entityType = 'REQUISITION' AND wi.entityId = r.id " +
           "AND wi.workflowDefinition.id = wsd.workflowDefinition.id " +
           "AND wi.currentStepNo = wsd.stepNo " +
           "AND wi.status = 'RUNNING' " +
           "AND r.status IN ('SUBMITTED', 'UNDER_APPROVAL') " +
           "AND (:roleCode IS NULL OR wsd.approverRoleCode = :roleCode) " +
           "AND (:departmentId IS NULL OR r.departmentId = :departmentId)")
    Page<Requisition> findPendingApprovalsByRoleAndDept(
            @Param("roleCode") String roleCode,
            @Param("departmentId") UUID departmentId,
            Pageable pageable
    );

    @Query("SELECT r FROM Requisition r, WorkflowInstance wi, WorkflowStepDefinition wsd " +
           "WHERE wi.entityType = 'REQUISITION' AND wi.entityId = r.id " +
           "AND wi.workflowDefinition.id = wsd.workflowDefinition.id " +
           "AND wi.currentStepNo = wsd.stepNo " +
           "AND wi.status = 'RUNNING' " +
           "AND r.status IN ('SUBMITTED', 'UNDER_APPROVAL') " +
           "AND wsd.approverRoleCode IN :roleCodes")
    Page<Requisition> findPendingApprovalsForRoles(
            @Param("roleCodes") Collection<String> roleCodes,
            Pageable pageable
    );

    @Query("SELECT r FROM Requisition r, WorkflowInstance wi " +
           "WHERE wi.entityType = 'REQUISITION' AND wi.entityId = r.id " +
           "AND wi.status = 'RUNNING' " +
           "AND r.status IN ('SUBMITTED', 'UNDER_APPROVAL')")
    Page<Requisition> findAllPendingApprovals(Pageable pageable);
}
