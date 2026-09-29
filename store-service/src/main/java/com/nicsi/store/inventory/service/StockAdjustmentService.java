package com.nicsi.store.inventory.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.rules.MakerCheckerGuard;
import com.nicsi.store.common.rules.MakerCheckerOperation;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockAdjustment;
import com.nicsi.store.inventory.domain.StockAdjustmentItem;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.dto.StockAdjustmentDto;
import com.nicsi.store.inventory.repository.InventoryLotRepository;
import com.nicsi.store.inventory.repository.StockAdjustmentRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.*;

@Service
public class StockAdjustmentService {

    private final StockAdjustmentRepository adjustmentRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final ItemRepository itemRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final InventoryPostingService inventoryPostingService;
    private final DocumentNumberService documentNumberService;
    private final MakerCheckerGuard makerCheckerGuard;
    private final AuditService auditService;

    public StockAdjustmentService(
            StockAdjustmentRepository adjustmentRepository,
            StockTransactionRepository stockTransactionRepository,
            StoreSiteRepository storeSiteRepository,
            ItemRepository itemRepository,
            StorageLocationRepository storageLocationRepository,
            InventoryLotRepository inventoryLotRepository,
            InventoryPostingService inventoryPostingService,
            DocumentNumberService documentNumberService,
            MakerCheckerGuard makerCheckerGuard,
            AuditService auditService
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.itemRepository = itemRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.inventoryLotRepository = inventoryLotRepository;
        this.inventoryPostingService = inventoryPostingService;
        this.documentNumberService = documentNumberService;
        this.makerCheckerGuard = makerCheckerGuard;
        this.auditService = auditService;
    }

    @Transactional
    public StockAdjustmentDto.Response createAdjustment(StockAdjustmentDto.CreateRequest request) {
        StoreSite store = storeSiteRepository.findById(request.storeId())
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND", "Store not found with id: " + request.storeId(), HttpStatus.NOT_FOUND));

        UUID currentUserId = CurrentUserHolder.getUserId();
        LocalDate adjDate = request.adjustmentDate() != null ? request.adjustmentDate() : LocalDate.now();
        String adjNo = documentNumberService.nextNumber("STOCK_ADJ", "ADJ", adjDate);

        StockAdjustment adj = new StockAdjustment();
        adj.setAdjustmentNo(adjNo);
        adj.setAdjustmentDate(adjDate);
        adj.setStore(store);
        adj.setReasonCode(request.reasonCode());
        adj.setReasonDetail(request.reasonDetail());
        adj.setStatus("DRAFT");
        adj.setCreatedBy(currentUserId);

        int lineNo = 1;
        for (StockAdjustmentDto.LineRequest lineReq : request.items()) {
            Item item = itemRepository.findById(lineReq.itemId())
                    .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found with id: " + lineReq.itemId(), HttpStatus.NOT_FOUND));
            StorageLocation location = storageLocationRepository.findById(lineReq.locationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found with id: " + lineReq.locationId(), HttpStatus.NOT_FOUND));

            InventoryLot lot = null;
            if (lineReq.lotId() != null) {
                lot = inventoryLotRepository.findById(lineReq.lotId()).orElse(null);
            }

            StockAdjustmentItem itemLine = new StockAdjustmentItem();
            itemLine.setLineNo(lineNo++);
            itemLine.setItem(item);
            itemLine.setLocation(location);
            itemLine.setLot(lot);
            itemLine.setDirection(lineReq.direction().toUpperCase().trim());
            itemLine.setQuantity(lineReq.quantity());
            itemLine.setUnitCost(lineReq.unitCost() != null ? lineReq.unitCost() : BigDecimal.ZERO);
            itemLine.setRemarks(lineReq.remarks());

            adj.addItem(itemLine);
        }

        StockAdjustment saved = adjustmentRepository.save(adj);

        auditService.record(new AuditEvent(
                "INVENTORY", "CREATE", "STOCK_ADJUSTMENT",
                saved.getId(), saved.getAdjustmentNo(), null,
                "Draft stock adjustment created: " + saved.getAdjustmentNo(),
                "Stock adjustment created"
        ));

        return toResponse(saved);
    }

    @Transactional
    public StockAdjustmentDto.Response submitAdjustment(UUID id) {
        StockAdjustment adj = adjustmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!"DRAFT".equals(adj.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Only DRAFT adjustment can be submitted. Current: " + adj.getStatus(), HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        adj.setStatus("SUBMITTED");
        StockAdjustment saved = adjustmentRepository.save(adj);

        auditService.record(new AuditEvent(
                "INVENTORY", "SUBMIT", "STOCK_ADJUSTMENT",
                saved.getId(), saved.getAdjustmentNo(), "DRAFT", "SUBMITTED",
                "Stock adjustment submitted for approval"
        ));

        return toResponse(saved);
    }

    @Transactional
    public StockAdjustmentDto.Response approveAdjustment(UUID id) {
        StockAdjustment adj = adjustmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found with id: " + id, HttpStatus.NOT_FOUND));

        if (!"SUBMITTED".equals(adj.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Only SUBMITTED adjustment can be approved. Current: " + adj.getStatus(), HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        MakerCheckerOperation op = "OPENING_BALANCE_CORRECTION".equals(adj.getReasonCode())
                ? MakerCheckerOperation.OPENING_BALANCE
                : MakerCheckerOperation.STOCK_ADJUSTMENT;

        // MAKER-CHECKER: creator cannot approve adjustment!
        makerCheckerGuard.assertDifferentUser(adj.getCreatedBy(), currentUserId, op);

        adj.setStatus("APPROVED");
        adj.setApprovedBy(currentUserId);
        adj.setApprovedAt(nowTruncatedToMicros());
        StockAdjustment saved = adjustmentRepository.save(adj);

        auditService.record(new AuditEvent(
                "INVENTORY", "APPROVE", "STOCK_ADJUSTMENT",
                saved.getId(), saved.getAdjustmentNo(), "SUBMITTED", "APPROVED",
                "Stock adjustment approved"
        ));

        return toResponse(saved);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public StockAdjustmentDto.Response postAdjustment(UUID id, String idempotencyKey) {
        StockAdjustment adj = adjustmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found with id: " + id, HttpStatus.NOT_FOUND));

        if ("POSTED".equals(adj.getStatus())) {
            return toResponse(adj); // Replay original on retry
        }

        if (!"APPROVED".equals(adj.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Only APPROVED adjustment can be posted. Current: " + adj.getStatus(), HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        MakerCheckerOperation op = "OPENING_BALANCE_CORRECTION".equals(adj.getReasonCode())
                ? MakerCheckerOperation.OPENING_BALANCE
                : MakerCheckerOperation.STOCK_ADJUSTMENT;

        // MAKER-CHECKER: creator cannot post adjustment!
        makerCheckerGuard.assertDifferentUser(adj.getCreatedBy(), currentUserId, op);

        UUID movementGroupId = UUID.randomUUID();

        for (StockAdjustmentItem itemLine : adj.getItems()) {
            boolean isIn = "IN".equalsIgnoreCase(itemLine.getDirection());
            String txnType = isIn ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT";
            if ("OPENING_BALANCE_CORRECTION".equals(adj.getReasonCode())) {
                txnType = isIn ? "OPENING" : "ADJUSTMENT_OUT";
            }

            String lineKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? String.format("%s:line-%d", idempotencyKey.trim(), itemLine.getLineNo())
                    : null;

            InventoryPostingRequest postReq = InventoryPostingRequest.builder()
                    .transactionType(txnType)
                    .item(itemLine.getItem())
                    .store(adj.getStore())
                    .location(itemLine.getLocation())
                    .lot(itemLine.getLot())
                    .quantityIn(isIn ? itemLine.getQuantity() : BigDecimal.ZERO)
                    .quantityOut(isIn ? BigDecimal.ZERO : itemLine.getQuantity())
                    .unitCost(itemLine.getUnitCost())
                    .referenceType("STOCK_ADJUSTMENT")
                    .referenceId(adj.getId())
                    .referenceNo(adj.getAdjustmentNo())
                    .movementGroupId(movementGroupId)
                    .idempotencyKey(lineKey)
                    .remarks(adj.getReasonCode() + ": " + adj.getReasonDetail())
                    .postedBy(currentUserId)
                    .build();

            inventoryPostingService.post(postReq);
        }

        adj.setStatus("POSTED");
        adj.setPostedBy(currentUserId);
        adj.setPostedAt(nowTruncatedToMicros());
        StockAdjustment saved = adjustmentRepository.save(adj);

        auditService.record(new AuditEvent(
                "INVENTORY", "POST", "STOCK_ADJUSTMENT",
                saved.getId(), saved.getAdjustmentNo(), "APPROVED", "POSTED",
                "Stock adjustment posted to ledger: " + saved.getAdjustmentNo()
        ));

        return toResponse(saved);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public StockAdjustmentDto.Response reverseTransaction(UUID transactionId, String reason, String idempotencyKey) {
        StockTransaction original = stockTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Transaction not found with id: " + transactionId, HttpStatus.NOT_FOUND));

        if ("REVERSAL".equalsIgnoreCase(original.getTransactionType())) {
            throw new BusinessException("INVALID_REVERSAL", "Cannot reverse a reversal transaction", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        // Maker-Checker: reversing user cannot be the user who originally posted the transaction!
        makerCheckerGuard.assertDifferentUser(original.getPostedBy(), currentUserId, MakerCheckerOperation.REVERSAL);

        // Inverse quantities
        boolean originalWasIn = original.getQuantityIn().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal revQtyIn = originalWasIn ? BigDecimal.ZERO : original.getQuantityOut();
        BigDecimal revQtyOut = originalWasIn ? original.getQuantityIn() : BigDecimal.ZERO;

        String revKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? String.format("%s:reversal-%s", idempotencyKey.trim(), original.getId())
                : null;

        InventoryPostingRequest postReq = InventoryPostingRequest.builder()
                .transactionType("REVERSAL")
                .item(original.getItem())
                .store(original.getStore())
                .location(original.getLocation())
                .lot(original.getLot())
                .quantityIn(revQtyIn)
                .quantityOut(revQtyOut)
                .unitCost(original.getUnitCost())
                .referenceType("REVERSAL")
                .referenceId(original.getId())
                .referenceNo(original.getTransactionNo())
                .movementGroupId(original.getMovementGroupId())
                .reversalOfTransaction(original)
                .idempotencyKey(revKey)
                .remarks("Reversal of " + original.getTransactionNo() + ": " + reason)
                .postedBy(currentUserId)
                .build();

        StockTransaction revTxn = inventoryPostingService.post(postReq);

        auditService.record(new AuditEvent(
                "INVENTORY", "REVERSAL", "STOCK_TRANSACTION",
                revTxn.getId(), revTxn.getTransactionNo(), null,
                "Reversal of " + original.getTransactionNo() + " posted: " + reason,
                "Transaction reversal"
        ));

        // Create adjustment record representing this reversal for reporting
        StockAdjustment adj = new StockAdjustment();
        adj.setAdjustmentNo(documentNumberService.nextNumber("STOCK_ADJ", "REV"));
        adj.setAdjustmentDate(LocalDate.now());
        adj.setStore(original.getStore());
        adj.setReasonCode("DATA_CORRECTION");
        adj.setReasonDetail("Reversal of " + original.getTransactionNo() + ": " + reason);
        adj.setStatus("POSTED");
        adj.setCreatedBy(currentUserId);
        adj.setApprovedBy(currentUserId);
        adj.setApprovedAt(nowTruncatedToMicros());
        adj.setPostedBy(currentUserId);
        adj.setPostedAt(nowTruncatedToMicros());

        StockAdjustmentItem line = new StockAdjustmentItem();
        line.setLineNo(1);
        line.setItem(original.getItem());
        line.setLocation(original.getLocation());
        line.setLot(original.getLot());
        line.setDirection(originalWasIn ? "OUT" : "IN");
        line.setQuantity(originalWasIn ? original.getQuantityIn() : original.getQuantityOut());
        line.setUnitCost(original.getUnitCost());
        line.setRemarks("Reversal of " + original.getTransactionNo());
        adj.addItem(line);

        StockAdjustment saved = adjustmentRepository.save(adj);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public StockAdjustmentDto.Response getAdjustment(UUID id) {
        StockAdjustment adj = adjustmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found with id: " + id, HttpStatus.NOT_FOUND));
        return toResponse(adj);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentDto.SummaryResponse> search(UUID storeId, String status, String reasonCode, Pageable pageable) {
        Page<StockAdjustment> page = adjustmentRepository.search(storeId, status, reasonCode, pageable);
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    /**
     * "Now" truncated to the precision PostgreSQL actually stores.
     *
     * A timestamptz column keeps microseconds, but Instant.now() carries nanoseconds. The response
     * of a live post is built from the in-memory entity (nanoseconds) while an idempotent replay is
     * built from the reloaded row (microseconds), so without this the same request returns two
     * different timestamps and the replay is not body-identical. Truncating at the point of
     * ASSIGNMENT makes the in-memory value and the stored value identical, so both paths agree.
     */
    private static Instant nowTruncatedToMicros() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public StockAdjustmentDto.Response toResponse(StockAdjustment adj) {
        List<StockAdjustmentDto.LineResponse> lines = new ArrayList<>();
        if (adj.getItems() != null) {
            for (StockAdjustmentItem item : adj.getItems()) {
                lines.add(new StockAdjustmentDto.LineResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getItem().getId(),
                        item.getItem().getItemCode(),
                        item.getItem().getItemName(),
                        item.getItem().getBaseUom() != null ? item.getItem().getBaseUom().getUomCode() : null,
                        item.getLocation().getId(),
                        item.getLocation().getLocationCode(),
                        item.getLot() != null ? item.getLot().getId() : null,
                        item.getLot() != null ? item.getLot().getLotNumber() : null,
                        item.getDirection(),
                        item.getQuantity(),
                        item.getUnitCost(),
                        item.getAsset() != null ? item.getAsset().getId() : null,
                        item.getRemarks()
                ));
            }
        }

        return new StockAdjustmentDto.Response(
                adj.getId(),
                adj.getAdjustmentNo(),
                adj.getAdjustmentDate(),
                adj.getStore().getId(),
                adj.getStore().getStoreCode(),
                adj.getStore().getStoreName(),
                adj.getReasonCode(),
                adj.getReasonDetail(),
                adj.getStatus(),
                adj.getCreatedAt(),
                adj.getCreatedBy(),
                adj.getApprovedAt(),
                adj.getApprovedBy(),
                adj.getPostedAt(),
                adj.getPostedBy(),
                lines
        );
    }

    public StockAdjustmentDto.SummaryResponse toSummaryResponse(StockAdjustment adj) {
        return new StockAdjustmentDto.SummaryResponse(
                adj.getId(),
                adj.getAdjustmentNo(),
                adj.getAdjustmentDate(),
                adj.getStore().getId(),
                adj.getStore().getStoreCode(),
                adj.getStore().getStoreName(),
                adj.getReasonCode(),
                adj.getStatus(),
                adj.getItems() != null ? adj.getItems().size() : 0,
                adj.getCreatedAt(),
                adj.getPostedAt()
        );
    }
}
