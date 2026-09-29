package com.nicsi.store.procurementref.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.procurementref.domain.PurchaseOrderItemRef;
import com.nicsi.store.procurementref.domain.PurchaseOrderRef;
import com.nicsi.store.procurementref.dto.PurchaseOrderDto;
import com.nicsi.store.procurementref.repository.PurchaseOrderRefRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRefRepository purchaseOrderRefRepository;
    private final ItemRepository itemRepository;
    private final AuditService auditService;

    public PurchaseOrderService(
            PurchaseOrderRefRepository purchaseOrderRefRepository,
            ItemRepository itemRepository,
            AuditService auditService
    ) {
        this.purchaseOrderRefRepository = purchaseOrderRefRepository;
        this.itemRepository = itemRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PurchaseOrderDto.Response createPurchaseOrder(PurchaseOrderDto.CreateRequest request) {
        if (purchaseOrderRefRepository.existsByPoNumber(request.poNumber())) {
            throw new BusinessException("PO_EXISTS", "Purchase Order with number " + request.poNumber() + " already exists", HttpStatus.CONFLICT);
        }

        PurchaseOrderRef po = new PurchaseOrderRef();
        if (request.sourceSystem() != null) po.setSourceSystem(request.sourceSystem());
        po.setSourcePoId(request.sourcePoId());
        po.setPoNumber(request.poNumber());
        po.setPoDate(request.poDate());
        po.setProcurementMode(request.procurementMode());
        po.setGemOrderNumber(request.gemOrderNumber());
        po.setContractNumber(request.contractNumber());
        po.setVendorId(request.vendorId());
        po.setVendorCodeSnapshot(request.vendorCodeSnapshot());
        po.setVendorNameSnapshot(request.vendorNameSnapshot());
        if (request.currencyCode() != null) po.setCurrencyCode(request.currencyCode());
        po.setRawSnapshot(request.rawSnapshot());
        po.setStatus("OPEN");

        BigDecimal calculatedTotal = BigDecimal.ZERO;

        for (PurchaseOrderDto.CreateItemRequest lineReq : request.items()) {
            PurchaseOrderItemRef itemRef = new PurchaseOrderItemRef();
            itemRef.setPoLineNo(lineReq.poLineNo());
            if (lineReq.itemId() != null) {
                Item item = itemRepository.findById(lineReq.itemId())
                        .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found with id: " + lineReq.itemId(), HttpStatus.NOT_FOUND));
                itemRef.setItem(item);
                if (lineReq.itemDescription() == null) {
                    itemRef.setItemDescription(item.getItemName());
                } else {
                    itemRef.setItemDescription(lineReq.itemDescription());
                }
            } else {
                itemRef.setItemDescription(lineReq.itemDescription());
            }

            itemRef.setOrderedQty(lineReq.orderedQty());
            BigDecimal unitRate = lineReq.unitRate() != null ? lineReq.unitRate() : BigDecimal.ZERO;
            BigDecimal taxAmount = lineReq.taxAmount() != null ? lineReq.taxAmount() : BigDecimal.ZERO;
            itemRef.setUnitRate(unitRate);
            itemRef.setTaxAmount(taxAmount);
            itemRef.setDeliveryDueDate(lineReq.deliveryDueDate());
            itemRef.setProjectId(lineReq.projectId());
            itemRef.setProjectCodeSnapshot(lineReq.projectCodeSnapshot());
            itemRef.setProjectNameSnapshot(lineReq.projectNameSnapshot());

            po.addItem(itemRef);

            calculatedTotal = calculatedTotal.add(lineReq.orderedQty().multiply(unitRate)).add(taxAmount);
        }

        if (request.totalAmount() != null) {
            po.setTotalAmount(request.totalAmount());
        } else {
            po.setTotalAmount(calculatedTotal);
        }

        PurchaseOrderRef saved = purchaseOrderRefRepository.save(po);

        auditService.record(new AuditEvent(
                "STORE", "CREATE", "PURCHASE_ORDER_REF",
                saved.getId(), saved.getPoNumber(), null,
                saved.getPoNumber() + " created with " + saved.getItems().size() + " items",
                "Purchase Order reference recorded"
        ));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto.Response getPurchaseOrder(UUID id) {
        PurchaseOrderRef po = purchaseOrderRefRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase Order not found with id: " + id, HttpStatus.NOT_FOUND));
        return toResponse(po);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto.Response getPurchaseOrderByNumber(String poNumber) {
        PurchaseOrderRef po = purchaseOrderRefRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase Order not found with number: " + poNumber, HttpStatus.NOT_FOUND));
        return toResponse(po);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderDto.SummaryResponse> search(String search, String status, Pageable pageable) {
        Page<PurchaseOrderRef> page = purchaseOrderRefRepository.search(search, status, pageable);
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    public PurchaseOrderDto.Response toResponse(PurchaseOrderRef po) {
        List<PurchaseOrderDto.ItemResponse> itemResponses = new ArrayList<>();
        if (po.getItems() != null) {
            for (PurchaseOrderItemRef item : po.getItems()) {
                BigDecimal remainingQty = item.getOrderedQty().subtract(item.getReceivedQty());
                if (remainingQty.compareTo(BigDecimal.ZERO) < 0) remainingQty = BigDecimal.ZERO;

                itemResponses.add(new PurchaseOrderDto.ItemResponse(
                        item.getId(),
                        item.getPoLineNo(),
                        item.getItem() != null ? item.getItem().getId() : null,
                        item.getItem() != null ? item.getItem().getItemCode() : null,
                        item.getItem() != null ? item.getItem().getItemName() : null,
                        item.getItemDescription(),
                        item.getOrderedQty(),
                        item.getReceivedQty(),
                        remainingQty,
                        item.getUnitRate(),
                        item.getTaxAmount(),
                        item.getDeliveryDueDate(),
                        item.getProjectId(),
                        item.getProjectCodeSnapshot(),
                        item.getProjectNameSnapshot()
                ));
            }
        }

        return new PurchaseOrderDto.Response(
                po.getId(),
                po.getSourceSystem(),
                po.getSourcePoId(),
                po.getPoNumber(),
                po.getPoDate(),
                po.getProcurementMode(),
                po.getGemOrderNumber(),
                po.getContractNumber(),
                po.getVendorId(),
                po.getVendorCodeSnapshot(),
                po.getVendorNameSnapshot(),
                po.getCurrencyCode(),
                po.getTotalAmount(),
                po.getStatus(),
                po.getRawSnapshot(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                itemResponses
        );
    }

    private PurchaseOrderDto.SummaryResponse toSummaryResponse(PurchaseOrderRef po) {
        return new PurchaseOrderDto.SummaryResponse(
                po.getId(),
                po.getSourceSystem(),
                po.getPoNumber(),
                po.getPoDate(),
                po.getProcurementMode(),
                po.getGemOrderNumber(),
                po.getVendorNameSnapshot(),
                po.getCurrencyCode(),
                po.getTotalAmount(),
                po.getStatus(),
                po.getItems() != null ? po.getItems().size() : 0,
                po.getCreatedAt()
        );
    }
}
