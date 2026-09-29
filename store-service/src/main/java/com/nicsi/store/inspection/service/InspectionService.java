package com.nicsi.store.inspection.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.rules.MakerCheckerGuard;
import com.nicsi.store.common.rules.MakerCheckerOperation;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.grn.domain.Grn;
import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.grn.repository.GrnItemRepository;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inspection.domain.Inspection;
import com.nicsi.store.inspection.domain.InspectionItem;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.inspection.repository.InspectionItemRepository;
import com.nicsi.store.inspection.repository.InspectionRepository;
import com.nicsi.store.procurementref.domain.PurchaseOrderItemRef;
import com.nicsi.store.procurementref.repository.PurchaseOrderItemRefRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final InspectionItemRepository inspectionItemRepository;
    private final GrnRepository grnRepository;
    private final GrnItemRepository grnItemRepository;
    private final PurchaseOrderItemRefRepository purchaseOrderItemRefRepository;
    private final DocumentNumberService documentNumberService;
    private final MakerCheckerGuard makerCheckerGuard;
    private final AuditService auditService;

    public InspectionService(
            InspectionRepository inspectionRepository,
            InspectionItemRepository inspectionItemRepository,
            GrnRepository grnRepository,
            GrnItemRepository grnItemRepository,
            PurchaseOrderItemRefRepository purchaseOrderItemRefRepository,
            DocumentNumberService documentNumberService,
            MakerCheckerGuard makerCheckerGuard,
            AuditService auditService
    ) {
        this.inspectionRepository = inspectionRepository;
        this.inspectionItemRepository = inspectionItemRepository;
        this.grnRepository = grnRepository;
        this.grnItemRepository = grnItemRepository;
        this.purchaseOrderItemRefRepository = purchaseOrderItemRefRepository;
        this.documentNumberService = documentNumberService;
        this.makerCheckerGuard = makerCheckerGuard;
        this.auditService = auditService;
    }

    @Transactional
    public Inspection initiateInspection(Grn grn) {
        Optional<Inspection> existing = inspectionRepository.findByGrnId(grn.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        LocalDate today = LocalDate.now();
        String inspectionNo = documentNumberService.nextNumber("INSPECTION", "INS", today);

        Inspection inspection = new Inspection();
        inspection.setInspectionNo(inspectionNo);
        inspection.setGrn(grn);
        inspection.setInspectionDate(today);
        inspection.setInspectedByUserId(currentUserId);
        inspection.setStatus("IN_PROGRESS");
        inspection.setCreatedBy(currentUserId);

        for (GrnItem grnItem : grn.getItems()) {
            InspectionItem item = new InspectionItem();
            item.setGrnItem(grnItem);
            item.setItem(grnItem.getItem());
            item.setInspectedQty(grnItem.getReceivedQty());
            item.setAcceptedQty(BigDecimal.ZERO);
            item.setRejectedQty(BigDecimal.ZERO);
            item.setQuarantineQty(BigDecimal.ZERO);
            inspection.addItem(item);
        }

        Inspection saved = inspectionRepository.save(inspection);

        auditService.record(new AuditEvent(
                "STORE", "INITIATE", "INSPECTION",
                saved.getId(), saved.getInspectionNo(), null,
                "Inspection initiated for GRN: " + grn.getGrnNo(),
                "Inward technical inspection workflow created"
        ));

        return saved;
    }

    @Transactional
    public InspectionDto.Response recordDecision(UUID inspectionId, InspectionDto.DecideRequest request) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException("INSPECTION_NOT_FOUND", "Inspection not found with id: " + inspectionId, HttpStatus.NOT_FOUND));

        if (!"IN_PROGRESS".equals(inspection.getStatus()) && !"DRAFT".equals(inspection.getStatus())) {
            throw new BusinessException("INSPECTION_ALREADY_DECIDED", "Inspection has already been decided: status " + inspection.getStatus(), HttpStatus.BAD_REQUEST);
        }

        if (!Objects.equals(inspection.getVersion(), request.version())) {
            throw new BusinessException("OPTIMISTIC_LOCK_FAILURE", "Inspection was modified by another transaction", HttpStatus.CONFLICT);
        }

        Grn grn = inspection.getGrn();
        UUID currentUserId = CurrentUserHolder.getUserId();

        // MAKER-CHECKER GUARD: The officer who received goods cannot inspect or approve inspection!
        makerCheckerGuard.assertDifferentUser(grn.getReceivedByUserId(), currentUserId, MakerCheckerOperation.TECHNICAL_INSPECTION);

        Map<UUID, InspectionDto.DecideItemRequest> decideMap = new HashMap<>();
        for (InspectionDto.DecideItemRequest dir : request.items()) {
            decideMap.put(dir.inspectionItemId(), dir);
        }

        BigDecimal totalInspected = BigDecimal.ZERO;
        BigDecimal totalAccepted = BigDecimal.ZERO;
        BigDecimal totalRejected = BigDecimal.ZERO;
        BigDecimal totalQuarantine = BigDecimal.ZERO;

        for (InspectionItem item : inspection.getItems()) {
            InspectionDto.DecideItemRequest dir = decideMap.get(item.getId());
            if (dir == null) {
                throw new BusinessException("MISSING_INSPECTION_ITEM_DECISION", "Decision missing for inspection item: " + item.getId(), HttpStatus.BAD_REQUEST);
            }

            BigDecimal sum = dir.acceptedQty().add(dir.rejectedQty()).add(dir.quarantineQty());
            if (sum.compareTo(item.getInspectedQty()) > 0) {
                throw new BusinessException(
                        "INSPECTION_QTY_EXCEEDED",
                        "Sum of accepted (" + dir.acceptedQty() + "), rejected (" + dir.rejectedQty() +
                                ") and quarantine (" + dir.quarantineQty() + ") exceeds inspected qty (" + item.getInspectedQty() + ")",
                        HttpStatus.BAD_REQUEST
                );
            }

            item.setAcceptedQty(dir.acceptedQty());
            item.setRejectedQty(dir.rejectedQty());
            item.setQuarantineQty(dir.quarantineQty());
            item.setSpecificationMatch(dir.specificationMatch());
            item.setPhysicalCondition(dir.physicalCondition());
            item.setWarrantyVerified(dir.warrantyVerified());
            item.setAccessoryVerified(dir.accessoryVerified());
            item.setTechnicalResult(dir.technicalResult());
            item.setRemarks(dir.remarks());

            // Update corresponding GRN item accepted/rejected quantities
            GrnItem grnItem = item.getGrnItem();
            grnItem.setAcceptedQty(dir.acceptedQty());
            grnItem.setRejectedQty(dir.rejectedQty());
            grnItemRepository.save(grnItem);

            // If PO line is referenced, update PO line received_qty by accepted quantity
            if (grnItem.getPurchaseOrderItemRef() != null && dir.acceptedQty().compareTo(BigDecimal.ZERO) > 0) {
                PurchaseOrderItemRef poItem = grnItem.getPurchaseOrderItemRef();
                poItem.setReceivedQty(poItem.getReceivedQty().add(dir.acceptedQty()));
                purchaseOrderItemRefRepository.save(poItem);
            }

            totalInspected = totalInspected.add(item.getInspectedQty());
            totalAccepted = totalAccepted.add(dir.acceptedQty());
            totalRejected = totalRejected.add(dir.rejectedQty());
            totalQuarantine = totalQuarantine.add(dir.quarantineQty());
        }

        // Determine final inspection and GRN statuses
        String finalStatus;
        String grnStatus;

        if (totalQuarantine.compareTo(BigDecimal.ZERO) > 0) {
            finalStatus = "QUARANTINE";
            grnStatus = "PARTIALLY_ACCEPTED";
        } else if (totalAccepted.compareTo(BigDecimal.ZERO) > 0 && totalRejected.compareTo(BigDecimal.ZERO) == 0) {
            finalStatus = "ACCEPTED";
            grnStatus = "ACCEPTED";
        } else if (totalAccepted.compareTo(BigDecimal.ZERO) == 0 && totalRejected.compareTo(BigDecimal.ZERO) > 0) {
            finalStatus = "REJECTED";
            grnStatus = "REJECTED";
        } else {
            finalStatus = "PARTIALLY_ACCEPTED";
            grnStatus = "PARTIALLY_ACCEPTED";
        }

        inspection.setStatus(finalStatus);
        inspection.setOverallRemarks(request.overallRemarks());
        inspection.setApprovedBy(currentUserId);
        inspection.setApprovedAt(Instant.now());
        inspection.setUpdatedBy(currentUserId);

        grn.setStatus(grnStatus);
        grn.setApprovedBy(currentUserId);
        grn.setApprovedAt(Instant.now());
        grn.setUpdatedBy(currentUserId);
        grnRepository.save(grn);

        Inspection saved = inspectionRepository.save(inspection);

        auditService.record(new AuditEvent(
                "STORE", "DECIDE", "INSPECTION",
                saved.getId(), saved.getInspectionNo(), "IN_PROGRESS",
                finalStatus, "Inspection completed: " + finalStatus + " (Accepted: " + totalAccepted + ", Rejected: " + totalRejected + ")"
        ));

        auditService.record(new AuditEvent(
                "STORE", "STATUS_CHANGE", "GRN",
                grn.getId(), grn.getGrnNo(), "UNDER_INSPECTION",
                grnStatus, "GRN status updated following inspection: " + grnStatus
        ));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InspectionDto.Response getInspection(UUID id) {
        Inspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("INSPECTION_NOT_FOUND", "Inspection not found with id: " + id, HttpStatus.NOT_FOUND));
        return toResponse(inspection);
    }

    @Transactional(readOnly = true)
    public InspectionDto.Response getInspectionByGrnId(UUID grnId) {
        Inspection inspection = inspectionRepository.findByGrnId(grnId)
                .orElseThrow(() -> new BusinessException("INSPECTION_NOT_FOUND", "Inspection not found for GRN id: " + grnId, HttpStatus.NOT_FOUND));
        return toResponse(inspection);
    }

    @Transactional(readOnly = true)
    public PageResponse<InspectionDto.SummaryResponse> search(String status, String search, Pageable pageable) {
        Page<Inspection> page = inspectionRepository.search(status, search, pageable);
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    public InspectionDto.Response toResponse(Inspection inspection) {
        List<InspectionDto.ItemResponse> itemResponses = new ArrayList<>();
        if (inspection.getItems() != null) {
            for (InspectionItem item : inspection.getItems()) {
                itemResponses.add(new InspectionDto.ItemResponse(
                        item.getId(),
                        item.getGrnItem() != null ? item.getGrnItem().getId() : null,
                        item.getItem().getId(),
                        item.getItem().getItemCode(),
                        item.getItem().getItemName(),
                        item.getItem().getBaseUom() != null ? item.getItem().getBaseUom().getUomCode() : null,
                        item.getInspectedQty(),
                        item.getAcceptedQty(),
                        item.getRejectedQty(),
                        item.getQuarantineQty(),
                        item.getSpecificationMatch(),
                        item.getPhysicalCondition(),
                        item.getWarrantyVerified(),
                        item.getAccessoryVerified(),
                        item.getTechnicalResult(),
                        item.getRemarks()
                ));
            }
        }

        return new InspectionDto.Response(
                inspection.getId(),
                inspection.getInspectionNo(),
                inspection.getGrn() != null ? inspection.getGrn().getId() : null,
                inspection.getGrn() != null ? inspection.getGrn().getGrnNo() : null,
                inspection.getInspectionDate(),
                inspection.getInspectedByUserId(),
                inspection.getInspectedByUserId() != null ? inspection.getInspectedByUserId().toString() : null,
                inspection.getStatus(),
                inspection.getOverallRemarks(),
                inspection.getApprovedBy(),
                inspection.getApprovedAt(),
                inspection.getCreatedAt(),
                inspection.getCreatedBy(),
                inspection.getUpdatedAt(),
                inspection.getUpdatedBy(),
                inspection.getVersion(),
                itemResponses
        );
    }

    private InspectionDto.SummaryResponse toSummaryResponse(Inspection inspection) {
        return new InspectionDto.SummaryResponse(
                inspection.getId(),
                inspection.getInspectionNo(),
                inspection.getGrn() != null ? inspection.getGrn().getId() : null,
                inspection.getGrn() != null ? inspection.getGrn().getGrnNo() : null,
                inspection.getInspectionDate(),
                inspection.getInspectedByUserId(),
                inspection.getStatus(),
                inspection.getOverallRemarks(),
                inspection.getItems() != null ? inspection.getItems().size() : 0,
                inspection.getCreatedAt()
        );
    }
}
