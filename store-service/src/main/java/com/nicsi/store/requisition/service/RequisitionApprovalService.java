package com.nicsi.store.requisition.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.rules.MakerCheckerGuard;
import com.nicsi.store.common.rules.MakerCheckerOperation;
import com.nicsi.store.common.security.CurrentUser;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.requisition.domain.Requisition;
import com.nicsi.store.requisition.domain.RequisitionItem;
import com.nicsi.store.requisition.dto.RequisitionDto;
import com.nicsi.store.requisition.repository.RequisitionRepository;
import com.nicsi.store.requisition.validation.RequisitionValidator;
import com.nicsi.store.workflow.domain.WorkflowInstance;
import com.nicsi.store.workflow.service.WorkflowEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class RequisitionApprovalService {

    private final RequisitionRepository requisitionRepository;
    private final RequisitionService requisitionService;
    private final WorkflowEngine workflowEngine;
    private final MakerCheckerGuard makerCheckerGuard;
    private final RequisitionValidator validator;
    private final AuditService auditService;

    public RequisitionApprovalService(
            RequisitionRepository requisitionRepository,
            RequisitionService requisitionService,
            WorkflowEngine workflowEngine,
            MakerCheckerGuard makerCheckerGuard,
            RequisitionValidator validator,
            AuditService auditService
    ) {
        this.requisitionRepository = requisitionRepository;
        this.requisitionService = requisitionService;
        this.workflowEngine = workflowEngine;
        this.makerCheckerGuard = makerCheckerGuard;
        this.validator = validator;
        this.auditService = auditService;
    }

    @Transactional
    public RequisitionDto.Response processDecision(UUID requisitionId, RequisitionDto.DecisionRequest request) {
        CurrentUser approver = CurrentUserHolder.get();
        Requisition req = requisitionRepository.findById(requisitionId)
                .orElseThrow(() -> new BusinessException("REQUISITION_NOT_FOUND", "Requisition not found with id: " + requisitionId, HttpStatus.NOT_FOUND));

        if (!"UNDER_APPROVAL".equals(req.getStatus()) && !"SUBMITTED".equals(req.getStatus())) {
            throw new BusinessException("REQUISITION_NOT_PENDING", "Requisition is not awaiting approval. Current status: " + req.getStatus(), HttpStatus.CONFLICT);
        }

        // MAKER-CHECKER ENFORCEMENT: Requester CANNOT approve/review their own requisition!
        makerCheckerGuard.assertDifferentUser(req.getRequesterUserId(), approver.userId(), MakerCheckerOperation.REQUISITION_APPROVE);

        String action = request.action().toUpperCase().trim();

        // Process line decisions if approving
        if ("APPROVE".equals(action)) {
            applyLineDecisions(req, request.lineDecisions());
        }

        // Delegate to Workflow Engine to advance or terminate workflow
        WorkflowInstance wf = workflowEngine.processAction(
                "REQUISITION",
                req.getId(),
                action,
                approver,
                request.comments(),
                null
        );

        // Update Requisition entity state based on workflow outcome
        if ("APPROVED".equalsIgnoreCase(wf.getStatus())) {
            req.setStatus("APPROVED");
            req.setClosedAt(Instant.now());
            req.setUpdatedBy(approver.userId());

            for (RequisitionItem item : req.getItems()) {
                if (item.getApprovedQty() == null) {
                    item.setApprovedQty(item.getRequestedQty());
                }
                item.setLineStatus("APPROVED");
                item.setUpdatedBy(approver.userId());
            }

            // Recalculate total estimated amount based on approved quantities
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (RequisitionItem item : req.getItems()) {
                BigDecimal qty = item.getApprovedQty() != null ? item.getApprovedQty() : BigDecimal.ZERO;
                BigDecimal rate = item.getEstimatedUnitRate() != null ? item.getEstimatedUnitRate() : BigDecimal.ZERO;
                totalAmount = totalAmount.add(qty.multiply(rate));
            }
            req.setTotalEstimatedAmount(totalAmount);

        } else if ("REJECTED".equalsIgnoreCase(wf.getStatus())) {
            req.setStatus("REJECTED");
            req.setClosedAt(Instant.now());
            req.setUpdatedBy(approver.userId());

            for (RequisitionItem item : req.getItems()) {
                item.setLineStatus("REJECTED");
                item.setUpdatedBy(approver.userId());
            }
        } else if ("RETURNED".equalsIgnoreCase(wf.getStatus())) {
            req.setStatus("DRAFT");
            req.setUpdatedBy(approver.userId());

            for (RequisitionItem item : req.getItems()) {
                item.setLineStatus("DRAFT");
                item.setUpdatedBy(approver.userId());
            }
        } else {
            // Still RUNNING at next step (e.g. STORE_VERIFICATION)
            req.setStatus("UNDER_APPROVAL");
            req.setUpdatedBy(approver.userId());
        }

        Requisition saved = requisitionRepository.save(req);

        auditService.record(AuditEvent.ofChange(
                "REQUISITION", action, "REQUISITION", saved.getId(), saved.getRequisitionNo(),
                null, Map.of("action", action, "comments", request.comments() != null ? request.comments() : "")
        ));

        return requisitionService.toResponse(saved, wf);
    }

    private void applyLineDecisions(Requisition req, List<RequisitionDto.LineDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            // Default: if no line decisions provided, set approvedQty = requestedQty for lines without one
            for (RequisitionItem line : req.getItems()) {
                if (line.getApprovedQty() == null) {
                    line.setApprovedQty(line.getRequestedQty());
                }
            }
            return;
        }

        Map<Integer, RequisitionDto.LineDecision> decisionMap = new HashMap<>();
        for (RequisitionDto.LineDecision d : decisions) {
            decisionMap.put(d.lineNo(), d);
        }

        for (RequisitionItem line : req.getItems()) {
            RequisitionDto.LineDecision d = decisionMap.get(line.getLineNo());
            if (d != null) {
                if (d.approvedQty() != null) {
                    if (d.approvedQty().compareTo(BigDecimal.ZERO) < 0) {
                        throw new BusinessException("INVALID_APPROVED_QTY", "Approved quantity cannot be negative for line " + line.getLineNo(), HttpStatus.BAD_REQUEST);
                    }
                    if (d.approvedQty().compareTo(line.getRequestedQty()) > 0) {
                        throw new BusinessException("APPROVED_QTY_EXCEEDS_REQUESTED", "Approved quantity (" + d.approvedQty() + ") cannot exceed requested quantity (" + line.getRequestedQty() + ") for line " + line.getLineNo(), HttpStatus.BAD_REQUEST);
                    }
                    validator.validateQuantityAgainstUom(d.approvedQty(), line.getItem().getBaseUom(), line.getLineNo());
                    line.setApprovedQty(d.approvedQty());
                }
            } else if (line.getApprovedQty() == null) {
                line.setApprovedQty(line.getRequestedQty());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<RequisitionDto.Response> getPendingApprovals(Pageable pageable) {
        CurrentUser user = CurrentUserHolder.get();

        Page<Requisition> page;
        if (user.hasRole("ROLE_ADMIN") || user.hasRole("ROLE_STORE_MANAGER")) {
            page = requisitionRepository.findAllPendingApprovals(pageable);
        } else if (user.hasRole("ROLE_HOD")) {
            page = requisitionRepository.findPendingApprovalsByRoleAndDept("ROLE_HOD", user.departmentId(), pageable);
        } else if (user.roles() != null && !user.roles().isEmpty()) {
            page = requisitionRepository.findPendingApprovalsForRoles(user.roles(), pageable);
        } else {
            page = new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        return page.map(r -> {
            WorkflowInstance wf = workflowEngine.findInstance("REQUISITION", r.getId()).orElse(null);
            return requisitionService.toResponse(r, wf);
        });
    }
}
