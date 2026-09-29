package com.nicsi.store.grn.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.grn.domain.Grn;
import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.grn.validation.GrnValidator;
import com.nicsi.store.inspection.service.InspectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GrnService {

    private final GrnRepository grnRepository;
    private final GrnValidator grnValidator;
    private final DocumentNumberService documentNumberService;
    private final InspectionService inspectionService;
    private final AuditService auditService;

    public GrnService(
            GrnRepository grnRepository,
            GrnValidator grnValidator,
            DocumentNumberService documentNumberService,
            InspectionService inspectionService,
            AuditService auditService
    ) {
        this.grnRepository = grnRepository;
        this.grnValidator = grnValidator;
        this.documentNumberService = documentNumberService;
        this.inspectionService = inspectionService;
        this.auditService = auditService;
    }

    @Transactional
    public GrnDto.Response createGrn(GrnDto.CreateRequest request) {
        GrnValidator.ValidatedGrnContext context = grnValidator.validateCreate(request);

        UUID currentUserId = CurrentUserHolder.getUserId();
        LocalDate grnDate = request.grnDate() != null ? request.grnDate() : LocalDate.now();
        String grnNo = documentNumberService.nextNumber("GRN", "GRN", grnDate);

        Grn grn = new Grn();
        grn.setGrnNo(grnNo);
        grn.setGrnDate(grnDate);
        grn.setStore(context.store());
        grn.setPurchaseOrderRef(context.purchaseOrderRef());
        grn.setVendorId(request.vendorId());
        grn.setVendorNameSnapshot(request.vendorNameSnapshot());
        grn.setInvoiceNumber(request.invoiceNumber());
        grn.setInvoiceDate(request.invoiceDate());
        grn.setChallanNumber(request.challanNumber());
        grn.setChallanDate(request.challanDate());
        grn.setReceivedByUserId(currentUserId);
        grn.setStatus("DRAFT");
        grn.setRemarks(request.remarks());
        grn.setCreatedBy(currentUserId);

        int lineNo = 1;
        for (GrnDto.CreateItemRequest lineReq : request.items()) {
            GrnItem item = new GrnItem();
            item.setLineNo(lineNo++);
            if (lineReq.poItemRefId() != null) {
                item.setPurchaseOrderItemRef(context.poItems().get(lineReq.poItemRefId()));
            }
            item.setItem(context.items().get(lineReq.itemId()));
            item.setReceivedQty(lineReq.receivedQty());
            item.setAcceptedQty(BigDecimal.ZERO);
            item.setRejectedQty(BigDecimal.ZERO);
            item.setUnitRate(lineReq.unitRate() != null ? lineReq.unitRate() : BigDecimal.ZERO);
            item.setReceivingLocation(context.locations().get(lineReq.receivingLocationId()));
            item.setBatchLotNo(lineReq.batchLotNo());
            item.setManufactureDate(lineReq.manufactureDate());
            item.setExpiryDate(lineReq.expiryDate());
            item.setRemarks(lineReq.remarks());

            grn.addItem(item);
        }

        Grn saved = grnRepository.save(grn);

        auditService.record(new AuditEvent(
                "STORE", "CREATE", "GRN",
                saved.getId(), saved.getGrnNo(), null,
                saved.getGrnNo() + " created with " + saved.getItems().size() + " items",
                "Goods Receipt Note draft created"
        ));

        return toResponse(saved);
    }

    @Transactional
    public GrnDto.Response updateGrn(UUID id, GrnDto.UpdateRequest request) {
        Grn grn = grnRepository.findById(id)
                .orElseThrow(() -> new BusinessException("GRN_NOT_FOUND", "GRN not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!"DRAFT".equals(grn.getStatus())) {
            throw new BusinessException("GRN_NOT_EDITABLE", "Only DRAFT GRN can be updated. Current status: " + grn.getStatus(), HttpStatus.BAD_REQUEST);
        }

        if (!Objects.equals(grn.getVersion(), request.version())) {
            throw new BusinessException("OPTIMISTIC_LOCK_FAILURE", "GRN was modified by another transaction", HttpStatus.CONFLICT);
        }

        GrnValidator.ValidatedGrnContext context = grnValidator.validateUpdate(request);
        UUID currentUserId = CurrentUserHolder.getUserId();

        if (request.grnDate() != null) grn.setGrnDate(request.grnDate());
        grn.setStore(context.store());
        grn.setPurchaseOrderRef(context.purchaseOrderRef());
        grn.setVendorId(request.vendorId());
        grn.setVendorNameSnapshot(request.vendorNameSnapshot());
        grn.setInvoiceNumber(request.invoiceNumber());
        grn.setInvoiceDate(request.invoiceDate());
        grn.setChallanNumber(request.challanNumber());
        grn.setChallanDate(request.challanDate());
        grn.setRemarks(request.remarks());
        grn.setUpdatedBy(currentUserId);

        grn.getItems().clear();
        int lineNo = 1;
        for (GrnDto.CreateItemRequest lineReq : request.items()) {
            GrnItem item = new GrnItem();
            item.setLineNo(lineNo++);
            if (lineReq.poItemRefId() != null) {
                item.setPurchaseOrderItemRef(context.poItems().get(lineReq.poItemRefId()));
            }
            item.setItem(context.items().get(lineReq.itemId()));
            item.setReceivedQty(lineReq.receivedQty());
            item.setAcceptedQty(BigDecimal.ZERO);
            item.setRejectedQty(BigDecimal.ZERO);
            item.setUnitRate(lineReq.unitRate() != null ? lineReq.unitRate() : BigDecimal.ZERO);
            item.setReceivingLocation(context.locations().get(lineReq.receivingLocationId()));
            item.setBatchLotNo(lineReq.batchLotNo());
            item.setManufactureDate(lineReq.manufactureDate());
            item.setExpiryDate(lineReq.expiryDate());
            item.setRemarks(lineReq.remarks());

            grn.addItem(item);
        }

        Grn saved = grnRepository.save(grn);

        auditService.record(new AuditEvent(
                "STORE", "UPDATE", "GRN",
                saved.getId(), saved.getGrnNo(), null,
                saved.getGrnNo() + " updated",
                "Goods Receipt Note updated"
        ));

        return toResponse(saved);
    }

    @Transactional
    public GrnDto.Response submitGrn(UUID id) {
        Grn grn = grnRepository.findById(id)
                .orElseThrow(() -> new BusinessException("GRN_NOT_FOUND", "GRN not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!"DRAFT".equals(grn.getStatus())) {
            throw new BusinessException("GRN_NOT_SUBMITTABLE", "Only DRAFT GRN can be submitted. Current status: " + grn.getStatus(), HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        grn.setStatus("UNDER_INSPECTION");
        grn.setUpdatedBy(currentUserId);

        Grn saved = grnRepository.save(grn);

        // Automatically trigger technical inspection workflow
        inspectionService.initiateInspection(saved);

        auditService.record(new AuditEvent(
                "STORE", "SUBMIT", "GRN",
                saved.getId(), saved.getGrnNo(), "DRAFT", "UNDER_INSPECTION",
                "Goods Receipt Note submitted for inspection"
        ));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public GrnDto.Response getGrn(UUID id) {
        Grn grn = grnRepository.findById(id)
                .orElseThrow(() -> new BusinessException("GRN_NOT_FOUND", "GRN not found with id: " + id, HttpStatus.NOT_FOUND));
        return toResponse(grn);
    }

    @Transactional(readOnly = true)
    public GrnDto.Response getGrnByNo(String grnNo) {
        Grn grn = grnRepository.findByGrnNo(grnNo)
                .orElseThrow(() -> new BusinessException("GRN_NOT_FOUND", "GRN not found with number: " + grnNo, HttpStatus.NOT_FOUND));
        return toResponse(grn);
    }

    @Transactional(readOnly = true)
    public PageResponse<GrnDto.SummaryResponse> search(UUID storeId, String status, String search, Pageable pageable) {
        Page<Grn> page = grnRepository.search(storeId, status, search, pageable);
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    public GrnDto.Response toResponse(Grn grn) {
        List<GrnDto.ItemResponse> itemResponses = new ArrayList<>();
        if (grn.getItems() != null) {
            for (GrnItem item : grn.getItems()) {
                itemResponses.add(new GrnDto.ItemResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getPurchaseOrderItemRef() != null ? item.getPurchaseOrderItemRef().getId() : null,
                        item.getPurchaseOrderItemRef() != null ? item.getPurchaseOrderItemRef().getPoLineNo() : null,
                        item.getItem().getId(),
                        item.getItem().getItemCode(),
                        item.getItem().getItemName(),
                        item.getItem().getBaseUom() != null ? item.getItem().getBaseUom().getUomCode() : null,
                        item.getReceivedQty(),
                        item.getAcceptedQty(),
                        item.getRejectedQty(),
                        item.getUnitRate(),
                        item.getReceivingLocation().getId(),
                        item.getReceivingLocation().getLocationCode(),
                        item.getReceivingLocation().getLocationName(),
                        item.getBatchLotNo(),
                        item.getManufactureDate(),
                        item.getExpiryDate(),
                        item.getRemarks()
                ));
            }
        }

        return new GrnDto.Response(
                grn.getId(),
                grn.getGrnNo(),
                grn.getGrnDate(),
                grn.getStore().getId(),
                grn.getStore().getStoreCode(),
                grn.getStore().getStoreName(),
                grn.getPurchaseOrderRef() != null ? grn.getPurchaseOrderRef().getId() : null,
                grn.getPurchaseOrderRef() != null ? grn.getPurchaseOrderRef().getPoNumber() : null,
                grn.getVendorId(),
                grn.getVendorNameSnapshot(),
                grn.getInvoiceNumber(),
                grn.getInvoiceDate(),
                grn.getChallanNumber(),
                grn.getChallanDate(),
                grn.getReceivedByUserId(),
                grn.getStatus(),
                grn.getRemarks(),
                grn.getCreatedAt(),
                grn.getCreatedBy(),
                grn.getUpdatedAt(),
                grn.getUpdatedBy(),
                grn.getApprovedAt(),
                grn.getApprovedBy(),
                grn.getVersion(),
                itemResponses
        );
    }

    private GrnDto.SummaryResponse toSummaryResponse(Grn grn) {
        BigDecimal totalReceived = BigDecimal.ZERO;
        if (grn.getItems() != null) {
            for (GrnItem item : grn.getItems()) {
                totalReceived = totalReceived.add(item.getReceivedQty());
            }
        }

        return new GrnDto.SummaryResponse(
                grn.getId(),
                grn.getGrnNo(),
                grn.getGrnDate(),
                grn.getStore().getId(),
                grn.getStore().getStoreName(),
                grn.getPurchaseOrderRef() != null ? grn.getPurchaseOrderRef().getId() : null,
                grn.getPurchaseOrderRef() != null ? grn.getPurchaseOrderRef().getPoNumber() : null,
                grn.getVendorNameSnapshot(),
                grn.getInvoiceNumber(),
                grn.getChallanNumber(),
                grn.getStatus(),
                grn.getItems() != null ? grn.getItems().size() : 0,
                totalReceived,
                grn.getCreatedAt()
        );
    }
}
