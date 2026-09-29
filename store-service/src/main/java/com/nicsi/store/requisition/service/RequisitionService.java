package com.nicsi.store.requisition.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUser;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.requisition.domain.Requisition;
import com.nicsi.store.requisition.domain.RequisitionItem;
import com.nicsi.store.requisition.dto.RequisitionDto;
import com.nicsi.store.requisition.repository.RequisitionItemRepository;
import com.nicsi.store.requisition.repository.RequisitionRepository;
import com.nicsi.store.requisition.validation.RequisitionValidator;
import com.nicsi.store.workflow.domain.WorkflowInstance;
import com.nicsi.store.workflow.dto.WorkflowDto;
import com.nicsi.store.workflow.service.WorkflowEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RequisitionService {

    private final RequisitionRepository requisitionRepository;
    private final RequisitionItemRepository itemRepository;
    private final RequisitionValidator validator;
    private final DocumentNumberService documentNumberService;
    private final WorkflowEngine workflowEngine;
    private final AuditService auditService;

    public RequisitionService(
            RequisitionRepository requisitionRepository,
            RequisitionItemRepository itemRepository,
            RequisitionValidator validator,
            DocumentNumberService documentNumberService,
            WorkflowEngine workflowEngine,
            AuditService auditService
    ) {
        this.requisitionRepository = requisitionRepository;
        this.itemRepository = itemRepository;
        this.validator = validator;
        this.documentNumberService = documentNumberService;
        this.workflowEngine = workflowEngine;
        this.auditService = auditService;
    }

    @Transactional
    public RequisitionDto.Response createRequisition(RequisitionDto.CreateRequest request) {
        CurrentUser user = CurrentUserHolder.get();
        Map<UUID, Item> itemMap = validator.validateCreate(request);

        String requisitionNo = documentNumberService.nextNumber("REQUISITION", "REQ");

        Requisition req = new Requisition();
        req.setRequisitionNo(requisitionNo);
        req.setRequisitionDate(LocalDate.now());
        req.setRequesterUserId(user.userId());
        req.setRequesterNameSnapshot(user.displayName() != null ? user.displayName() : user.username());
        req.setDepartmentId(request.departmentId() != null ? request.departmentId() : user.departmentId());
        req.setDepartmentCodeSnapshot(request.departmentCodeSnapshot());
        req.setDepartmentNameSnapshot(request.departmentNameSnapshot());
        req.setDivisionId(request.divisionId());
        req.setDivisionNameSnapshot(request.divisionNameSnapshot());
        req.setProjectId(request.projectId());
        req.setProjectCodeSnapshot(request.projectCodeSnapshot());
        req.setProjectNameSnapshot(request.projectNameSnapshot());
        req.setPurpose(request.purpose());
        req.setPriority(request.priority());
        req.setRequiredByDate(request.requiredByDate());
        req.setStatus("DRAFT");
        req.setCreatedBy(user.userId());
        req.setUpdatedBy(user.userId());

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<RequisitionItem> lines = new ArrayList<>();
        for (int i = 0; i < request.items().size(); i++) {
            RequisitionDto.LineRequest lineReq = request.items().get(i);
            Item item = itemMap.get(lineReq.itemId());

            RequisitionItem line = new RequisitionItem();
            line.setRequisition(req);
            line.setLineNo(i + 1);
            line.setItem(item);
            line.setRequestedQty(lineReq.requestedQty());
            line.setEstimatedUnitRate(lineReq.estimatedUnitRate() != null ? lineReq.estimatedUnitRate() : item.getStandardRate());
            line.setSpecification(lineReq.specification());
            line.setJustification(lineReq.justification());
            line.setPreferredMakeModel(lineReq.preferredMakeModel());
            line.setLineStatus("DRAFT");
            line.setCreatedBy(user.userId());
            line.setUpdatedBy(user.userId());

            if (line.getEstimatedUnitRate() != null) {
                totalAmount = totalAmount.add(line.getRequestedQty().multiply(line.getEstimatedUnitRate()));
            }
            lines.add(line);
        }

        req.setTotalEstimatedAmount(totalAmount);
        req.setItems(lines);

        Requisition saved = requisitionRepository.save(req);

        auditService.record(AuditEvent.of(
                "REQUISITION", "CREATE", "REQUISITION", saved.getId(), saved.getRequisitionNo()
        ));

        return toResponse(saved, null);
    }

    @Transactional
    public RequisitionDto.Response updateRequisition(UUID id, RequisitionDto.UpdateRequest request) {
        CurrentUser user = CurrentUserHolder.get();
        Requisition req = requisitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("REQUISITION_NOT_FOUND", "Requisition not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!user.userId().equals(req.getRequesterUserId()) && !user.hasRole("ROLE_ADMIN")) {
            throw new BusinessException("REQUISITION_FORBIDDEN", "Only the original requester may update this requisition", HttpStatus.FORBIDDEN);
        }

        if (!"DRAFT".equals(req.getStatus()) && !"RETURNED".equals(req.getStatus())) {
            throw new BusinessException("REQUISITION_NOT_EDITABLE", "Cannot edit requisition in status: " + req.getStatus(), HttpStatus.CONFLICT);
        }

        if (!Objects.equals(req.getVersion(), request.version())) {
            throw new BusinessException("OPTIMISTIC_LOCK_ERROR", "Requisition was updated by another process. Please refresh and try again.", HttpStatus.CONFLICT);
        }

        Map<UUID, Item> itemMap = validator.validateUpdate(request);

        req.setDepartmentId(request.departmentId());
        req.setDepartmentCodeSnapshot(request.departmentCodeSnapshot());
        req.setDepartmentNameSnapshot(request.departmentNameSnapshot());
        req.setDivisionId(request.divisionId());
        req.setDivisionNameSnapshot(request.divisionNameSnapshot());
        req.setProjectId(request.projectId());
        req.setProjectCodeSnapshot(request.projectCodeSnapshot());
        req.setProjectNameSnapshot(request.projectNameSnapshot());
        req.setPurpose(request.purpose());
        req.setPriority(request.priority());
        req.setRequiredByDate(request.requiredByDate());
        req.setUpdatedBy(user.userId());

        req.getItems().clear();
        requisitionRepository.saveAndFlush(req);
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int i = 0; i < request.items().size(); i++) {
            RequisitionDto.LineRequest lineReq = request.items().get(i);
            Item item = itemMap.get(lineReq.itemId());

            RequisitionItem line = new RequisitionItem();
            line.setRequisition(req);
            line.setLineNo(i + 1);
            line.setItem(item);
            line.setRequestedQty(lineReq.requestedQty());
            line.setEstimatedUnitRate(lineReq.estimatedUnitRate() != null ? lineReq.estimatedUnitRate() : item.getStandardRate());
            line.setSpecification(lineReq.specification());
            line.setJustification(lineReq.justification());
            line.setPreferredMakeModel(lineReq.preferredMakeModel());
            line.setLineStatus("DRAFT");
            line.setCreatedBy(user.userId());
            line.setUpdatedBy(user.userId());

            if (line.getEstimatedUnitRate() != null) {
                totalAmount = totalAmount.add(line.getRequestedQty().multiply(line.getEstimatedUnitRate()));
            }
            req.getItems().add(line);
        }

        req.setTotalEstimatedAmount(totalAmount);
        Requisition updated = requisitionRepository.save(req);

        auditService.record(AuditEvent.of(
                "REQUISITION", "UPDATE", "REQUISITION", updated.getId(), updated.getRequisitionNo()
        ));

        WorkflowInstance wf = workflowEngine.findInstance("REQUISITION", updated.getId()).orElse(null);
        return toResponse(updated, wf);
    }

    @Transactional
    public RequisitionDto.Response submitRequisition(UUID id) {
        CurrentUser user = CurrentUserHolder.get();
        Requisition req = requisitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("REQUISITION_NOT_FOUND", "Requisition not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!user.userId().equals(req.getRequesterUserId()) && !user.hasRole("ROLE_ADMIN")) {
            throw new BusinessException("REQUISITION_FORBIDDEN", "Only the original requester may submit this requisition", HttpStatus.FORBIDDEN);
        }

        if (!"DRAFT".equals(req.getStatus()) && !"RETURNED".equals(req.getStatus())) {
            throw new BusinessException("REQUISITION_INVALID_STATUS", "Only DRAFT or RETURNED requisitions can be submitted", HttpStatus.CONFLICT);
        }

        if (req.getItems().isEmpty()) {
            throw new BusinessException("REQUISITION_EMPTY_ITEMS", "Cannot submit requisition without items", HttpStatus.BAD_REQUEST);
        }

        req.setStatus("UNDER_APPROVAL");
        req.setSubmittedAt(Instant.now());
        req.setUpdatedBy(user.userId());

        for (RequisitionItem item : req.getItems()) {
            item.setUpdatedBy(user.userId());
        }

        Requisition saved = requisitionRepository.save(req);

        WorkflowInstance wf = workflowEngine.startWorkflow("REQUISITION", saved.getId(), user.userId(), "REQ_STANDARD_V1");

        auditService.record(AuditEvent.of(
                "REQUISITION", "SUBMIT", "REQUISITION", saved.getId(), saved.getRequisitionNo()
        ));

        return toResponse(saved, wf);
    }

    @Transactional
    public RequisitionDto.Response cancelRequisition(UUID id, RequisitionDto.CancelRequest request) {
        CurrentUser user = CurrentUserHolder.get();
        Requisition req = requisitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("REQUISITION_NOT_FOUND", "Requisition not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!user.userId().equals(req.getRequesterUserId()) && !user.hasRole("ROLE_ADMIN")) {
            throw new BusinessException("REQUISITION_FORBIDDEN", "Only the requester or admin can cancel this requisition", HttpStatus.FORBIDDEN);
        }

        if ("APPROVED".equals(req.getStatus()) || "ISSUED".equals(req.getStatus()) || "CANCELLED".equals(req.getStatus())) {
            throw new BusinessException("REQUISITION_CANNOT_CANCEL", "Cannot cancel requisition in status: " + req.getStatus(), HttpStatus.CONFLICT);
        }

        req.setStatus("CANCELLED");
        req.setClosedAt(Instant.now());
        req.setUpdatedBy(user.userId());

        for (RequisitionItem item : req.getItems()) {
            item.setLineStatus("CANCELLED");
            item.setUpdatedBy(user.userId());
        }

        Requisition saved = requisitionRepository.save(req);
        workflowEngine.cancelWorkflow("REQUISITION", saved.getId(), user.userId(), request != null ? request.reason() : "Cancelled by requester");

        auditService.record(AuditEvent.ofChange(
                "REQUISITION", "CANCEL", "REQUISITION", saved.getId(), saved.getRequisitionNo(),
                null, Map.of("reason", request != null && request.reason() != null ? request.reason() : "")
        ));

        WorkflowInstance wf = workflowEngine.findInstance("REQUISITION", saved.getId()).orElse(null);
        return toResponse(saved, wf);
    }

    @Transactional(readOnly = true)
    public RequisitionDto.Response getRequisition(UUID id) {
        Requisition req = requisitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("REQUISITION_NOT_FOUND", "Requisition not found with id: " + id, HttpStatus.NOT_FOUND));

        CurrentUser user = CurrentUserHolder.get();
        if (!user.userId().equals(req.getRequesterUserId())
                && !user.hasPermission("REQUISITION_VIEW")
                && !user.hasPermission("REQUISITION_APPROVE")
                && !user.hasRole("ROLE_ADMIN")) {
            throw new BusinessException("REQUISITION_FORBIDDEN", "You are not authorized to view this requisition", HttpStatus.FORBIDDEN);
        }

        WorkflowInstance wf = workflowEngine.findInstance("REQUISITION", req.getId()).orElse(null);
        return toResponse(req, wf);
    }

    @Transactional(readOnly = true)
    public Page<RequisitionDto.Response> search(
            String search,
            String status,
            String priority,
            UUID requesterUserId,
            UUID departmentId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        CurrentUser user = CurrentUserHolder.get();

        // If user does not have REQUISITION_VIEW or STORE_ADMIN, restrict to their own requisitions
        UUID effectiveRequesterId = requesterUserId;
        if (!user.hasPermission("REQUISITION_VIEW") && !user.hasRole("ROLE_ADMIN") && !user.hasRole("ROLE_STORE_MANAGER")) {
            effectiveRequesterId = user.userId();
        }

        Page<Requisition> page = requisitionRepository.search(
                search, status, priority, effectiveRequesterId, departmentId, fromDate, toDate, pageable
        );

        return page.map(r -> {
            WorkflowInstance wf = workflowEngine.findInstance("REQUISITION", r.getId()).orElse(null);
            return toResponse(r, wf);
        });
    }

    public RequisitionDto.Response toResponse(Requisition r, WorkflowInstance wf) {
        List<RequisitionDto.LineResponse> lines = r.getItems().stream()
                .map(item -> new RequisitionDto.LineResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getItem().getId(),
                        item.getItem().getItemCode(),
                        item.getItem().getItemName(),
                        item.getItem().getBaseUom() != null ? item.getItem().getBaseUom().getUomCode() : null,
                        item.getRequestedQty(),
                        item.getApprovedQty(),
                        item.getIssuedQty(),
                        item.getEstimatedUnitRate(),
                        item.getSpecification(),
                        item.getJustification(),
                        item.getPreferredMakeModel(),
                        item.getLineStatus(),
                        item.getCreatedAt(),
                        item.getCreatedBy(),
                        item.getUpdatedAt(),
                        item.getUpdatedBy(),
                        item.getVersion()
                )).collect(Collectors.toList());

        WorkflowDto.InstanceResponse wfDto = wf != null ? workflowEngine.toInstanceResponse(wf) : null;

        return new RequisitionDto.Response(
                r.getId(),
                r.getRequisitionNo(),
                r.getRequisitionDate(),
                r.getRequesterUserId(),
                r.getRequesterNameSnapshot(),
                r.getDepartmentId(),
                r.getDepartmentCodeSnapshot(),
                r.getDepartmentNameSnapshot(),
                r.getDivisionId(),
                r.getDivisionNameSnapshot(),
                r.getProjectId(),
                r.getProjectCodeSnapshot(),
                r.getProjectNameSnapshot(),
                r.getPurpose(),
                r.getPriority(),
                r.getRequiredByDate(),
                r.getStatus(),
                r.getTotalEstimatedAmount(),
                r.getSubmittedAt(),
                r.getClosedAt(),
                r.getCreatedAt(),
                r.getCreatedBy(),
                r.getUpdatedAt(),
                r.getUpdatedBy(),
                r.getVersion(),
                lines,
                wfDto
        );
    }
}
