package com.nicsi.store.issue.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockReservation;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.repository.InventoryLotRepository;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockReservationRepository;
import com.nicsi.store.inventory.service.InventoryPostingRequest;
import com.nicsi.store.inventory.service.InventoryPostingService;
import com.nicsi.store.issue.domain.AssetAssignment;
import com.nicsi.store.issue.domain.IssueHeader;
import com.nicsi.store.issue.domain.IssueItem;
import com.nicsi.store.issue.dto.IssueDto;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
import com.nicsi.store.issue.repository.IssueHeaderRepository;
import com.nicsi.store.issue.repository.IssueItemRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.requisition.domain.RequisitionItem;
import com.nicsi.store.requisition.repository.RequisitionItemRepository;
import com.nicsi.store.requisition.repository.RequisitionRepository;
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

/**
 * Core service for Issue (stock-out) workflow.
 *
 * State flow:
 *   create()         → DRAFT
 *   submit()         → SUBMITTED  (triggers workflow/approval if policy demands it)
 *   approve()        → APPROVED   (by store manager / ISSUE_APPROVE authority)
 *   post()           → POSTED     (stock deducted, assets assigned — atomic, idempotent)
 *   acknowledge()    → ACKNOWLEDGED / PARTIALLY_ACKNOWLEDGED
 *
 * Point-5 from blueprint: all state changes inside ONE transaction (Propagation.REQUIRED).
 */
@Service
public class IssueService {

    private final IssueHeaderRepository issueHeaderRepository;
    private final IssueItemRepository issueItemRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final InventoryPostingService inventoryPostingService;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockReservationRepository stockReservationRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final AssetRepository assetRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final ItemRepository itemRepository;
    private final RequisitionRepository requisitionRepository;
    private final RequisitionItemRepository requisitionItemRepository;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public IssueService(
            IssueHeaderRepository issueHeaderRepository,
            IssueItemRepository issueItemRepository,
            AssetAssignmentRepository assetAssignmentRepository,
            InventoryPostingService inventoryPostingService,
            StockBalanceRepository stockBalanceRepository,
            StockReservationRepository stockReservationRepository,
            InventoryLotRepository inventoryLotRepository,
            AssetRepository assetRepository,
            StoreSiteRepository storeSiteRepository,
            StorageLocationRepository storageLocationRepository,
            ItemRepository itemRepository,
            RequisitionRepository requisitionRepository,
            RequisitionItemRepository requisitionItemRepository,
            DocumentNumberService documentNumberService,
            AuditService auditService
    ) {
        this.issueHeaderRepository = issueHeaderRepository;
        this.issueItemRepository = issueItemRepository;
        this.assetAssignmentRepository = assetAssignmentRepository;
        this.inventoryPostingService = inventoryPostingService;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.inventoryLotRepository = inventoryLotRepository;
        this.assetRepository = assetRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.itemRepository = itemRepository;
        this.requisitionRepository = requisitionRepository;
        this.requisitionItemRepository = requisitionItemRepository;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE ISSUE DRAFT
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public IssueDto.Response createIssue(IssueDto.CreateRequest request) {
        UUID currentUserId = CurrentUserHolder.getUserId();

        StoreSite store = storeSiteRepository.findById(request.storeId())
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND",
                        "Store not found: " + request.storeId(), HttpStatus.NOT_FOUND));

        String issueNo = documentNumberService.nextNumber("ISSUE", "ISS", request.issueDate() != null ? request.issueDate() : LocalDate.now());

        IssueHeader header = new IssueHeader();
        header.setIssueNo(issueNo);
        header.setIssueDate(request.issueDate() != null ? request.issueDate() : LocalDate.now());
        header.setStore(store);
        header.setIssuedToType(request.issuedToType());
        header.setIssuedToUserId(request.issuedToUserId());
        header.setIssuedToNameSnapshot(request.issuedToNameSnapshot());
        header.setDepartmentId(request.departmentId());
        header.setDepartmentNameSnapshot(request.departmentNameSnapshot());
        header.setProjectId(request.projectId());
        header.setProjectNameSnapshot(request.projectNameSnapshot());
        header.setPurpose(request.purpose());
        header.setStatus("DRAFT");
        header.setCreatedBy(currentUserId);
        header.setUpdatedBy(currentUserId);

        if (request.requisitionId() != null) {
            header.setRequisition(requisitionRepository.findById(request.requisitionId())
                    .orElseThrow(() -> new BusinessException("REQUISITION_NOT_FOUND",
                            "Requisition not found: " + request.requisitionId(), HttpStatus.NOT_FOUND)));
        }

        int lineNo = 1;
        for (IssueDto.CreateItemRequest lineReq : request.items()) {
            Item item = itemRepository.findById(lineReq.itemId())
                    .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND",
                            "Item not found: " + lineReq.itemId(), HttpStatus.NOT_FOUND));

            StorageLocation location = storageLocationRepository.findById(lineReq.locationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND",
                            "Location not found: " + lineReq.locationId(), HttpStatus.NOT_FOUND));

            // Validate location belongs to the same store
            if (!location.getStore().getId().equals(store.getId())) {
                throw new BusinessException("LOCATION_STORE_MISMATCH",
                        "Location " + location.getLocationCode() + " does not belong to store " + store.getStoreCode(),
                        HttpStatus.BAD_REQUEST);
            }

            IssueItem lineItem = new IssueItem();
            lineItem.setIssue(header);
            lineItem.setLineNo(lineReq.lineNo() > 0 ? lineReq.lineNo() : lineNo);
            lineItem.setItem(item);
            lineItem.setLocation(location);
            lineItem.setIssueQty(lineReq.issueQty());
            lineItem.setUnitCost(BigDecimal.ZERO); // will be set from stock_balance at post time
            lineItem.setRemarks(lineReq.remarks());

            if (lineReq.reservationId() != null) {
                StockReservation reservation = stockReservationRepository.findById(lineReq.reservationId())
                        .orElseThrow(() -> new BusinessException("RESERVATION_NOT_FOUND",
                                "Reservation not found: " + lineReq.reservationId(), HttpStatus.NOT_FOUND));
                lineItem.setReservation(reservation);
            }

            if (lineReq.lotId() != null) {
                InventoryLot lot = inventoryLotRepository.findById(lineReq.lotId())
                        .orElseThrow(() -> new BusinessException("LOT_NOT_FOUND",
                                "Lot not found: " + lineReq.lotId(), HttpStatus.NOT_FOUND));
                lineItem.setLot(lot);
            }

            if (lineReq.requisitionItemId() != null) {
                RequisitionItem reqItem = requisitionItemRepository.findById(lineReq.requisitionItemId())
                        .orElseThrow(() -> new BusinessException("REQUISITION_ITEM_NOT_FOUND",
                                "Requisition item not found: " + lineReq.requisitionItemId(), HttpStatus.NOT_FOUND));
                lineItem.setRequisitionItem(reqItem);
            }

            header.getItems().add(lineItem);
            lineNo++;
        }

        IssueHeader saved = issueHeaderRepository.save(header);

        auditService.record(new AuditEvent(
                "ISSUE", "CREATE", "ISSUE_HEADER", saved.getId(), saved.getIssueNo(),
                null, null, null
        ));

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBMIT
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public IssueDto.Response submitIssue(UUID issueId) {
        UUID currentUserId = CurrentUserHolder.getUserId();
        IssueHeader header = findOrThrow(issueId);

        if (!"DRAFT".equals(header.getStatus())) {
            throw new BusinessException("INVALID_STATE",
                    "Issue must be in DRAFT to submit. Current status: " + header.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        header.setStatus("SUBMITTED");
        header.setUpdatedBy(currentUserId);
        header.setUpdatedAt(Instant.now());

        auditService.record(new AuditEvent(
                "ISSUE", "SUBMIT", "ISSUE_HEADER", issueId, header.getIssueNo(),
                "DRAFT", "SUBMITTED", null
        ));

        return toResponse(issueHeaderRepository.save(header));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APPROVE
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public IssueDto.Response approveIssue(UUID issueId) {
        UUID currentUserId = CurrentUserHolder.getUserId();
        IssueHeader header = findOrThrow(issueId);

        if (!"SUBMITTED".equals(header.getStatus())) {
            throw new BusinessException("INVALID_STATE",
                    "Issue must be SUBMITTED to approve. Current status: " + header.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        header.setStatus("APPROVED");
        header.setUpdatedBy(currentUserId);
        header.setUpdatedAt(Instant.now());

        auditService.record(new AuditEvent(
                "ISSUE", "APPROVE", "ISSUE_HEADER", issueId, header.getIssueNo(),
                "SUBMITTED", "APPROVED", null
        ));

        return toResponse(issueHeaderRepository.save(header));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REJECT
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public IssueDto.Response rejectIssue(UUID issueId, String remarks) {
        UUID currentUserId = CurrentUserHolder.getUserId();
        IssueHeader header = findOrThrow(issueId);

        if (!Set.of("SUBMITTED", "APPROVED").contains(header.getStatus())) {
            throw new BusinessException("INVALID_STATE",
                    "Issue must be SUBMITTED or APPROVED to reject. Current status: " + header.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String oldStatus = header.getStatus();
        header.setStatus("REJECTED");
        header.setUpdatedBy(currentUserId);
        header.setUpdatedAt(Instant.now());

        auditService.record(new AuditEvent(
                "ISSUE", "REJECT", "ISSUE_HEADER", issueId, header.getIssueNo(),
                oldStatus, "REJECTED", remarks
        ));

        return toResponse(issueHeaderRepository.save(header));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST ISSUE  (stock deduction + asset assignment, maker-checker enforced)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Posts the issue:
     * 1. Validates state (SUBMITTED or APPROVED) and maker-checker (poster ≠ creator).
     * 2. Idempotency: if any line already has a stock_transaction with the derived key,
     *    returns the previously committed PostResult without re-executing.
     * 3. For each line: deducts stock via InventoryPostingService (ISSUE transaction type),
     *    updates the unit_cost captured from average cost, releases reservation if linked.
     * 4. For asset (NON_CONSUMABLE / SERIAL) lines: marks assets as ISSUED and creates
     *    AssetAssignment records.
     * 5. Transitions issue to POSTED.
     * 6. All steps share one transaction (Propagation.REQUIRED throughout).
     */
    @Transactional
    public IssueDto.PostResult postIssue(UUID issueId, IssueDto.PostRequest request, String idempotencyKey) {
        UUID currentUserId = CurrentUserHolder.getUserId();
        IssueHeader header = findOrThrow(issueId);

        // Maker-checker: poster must differ from creator
        if (header.getCreatedBy() != null && header.getCreatedBy().equals(currentUserId)) {
            throw new BusinessException("MAKER_CHECKER_VIOLATION",
                    "The user who created the issue cannot also post it.",
                    HttpStatus.FORBIDDEN);
        }

        // Idempotency: check if this exact key was already committed for this issue
        // (if line 1 committed, the whole thing committed atomically)
        if ("POSTED".equals(header.getStatus())) {
            // Already posted — return idempotent result
            return buildPostResult(header, currentUserId);
        }

        // Only SUBMITTED or APPROVED can be posted (some configs auto-approve on submit)
        if (!Set.of("SUBMITTED", "APPROVED").contains(header.getStatus())) {
            throw new BusinessException("INVALID_STATE",
                    "Issue must be SUBMITTED or APPROVED to post. Current status: " + header.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // Derive the per-issue idempotency root key
        String idemRoot = idempotencyKey != null ? idempotencyKey : ("ISS-POST-" + issueId);

        // Build lineAssets lookup: issueItemId → List<assetId>
        Map<UUID, List<UUID>> lineAssetMap = new HashMap<>();
        if (request != null && request.lineAssets() != null) {
            for (IssueDto.LineAssetRequest lar : request.lineAssets()) {
                lineAssetMap.put(lar.issueItemId(), lar.assetIds());
            }
        }

        UUID movementGroupId = UUID.randomUUID();
        int postedLines = 0;
        int assetsAssigned = 0;

        List<IssueItem> lines = header.getItems();
        for (int i = 0; i < lines.size(); i++) {
            IssueItem line = lines.get(i);
            String lineIdemKey = idemRoot + ":" + line.getLineNo();

            // Snapshot avg cost from stock balance at post time
            Optional<StockBalance> balanceOpt = stockBalanceRepository.findByDimensionsForUpdate(
                    line.getItem().getId(),
                    header.getStore().getId(),
                    line.getLocation().getId(),
                    line.getLot() != null ? line.getLot().getId() : null
            );
            BigDecimal unitCostAtPost = balanceOpt
                    .map(StockBalance::getAvgUnitCost)
                    .orElse(BigDecimal.ZERO);

            // Post the stock OUT transaction
            InventoryPostingRequest postReq = InventoryPostingRequest.builder()
                    .transactionType("ISSUE")
                    .item(line.getItem())
                    .store(header.getStore())
                    .location(line.getLocation())
                    .lot(line.getLot())
                    .quantityOut(line.getIssueQty())
                    .unitCost(unitCostAtPost)
                    .referenceType("ISSUE")
                    .referenceId(header.getId())
                    .referenceNo(header.getIssueNo())
                    .movementGroupId(movementGroupId)
                    .idempotencyKey(lineIdemKey)
                    .remarks(line.getRemarks())
                    .postedBy(currentUserId)
                    .build();

            StockTransaction txn = inventoryPostingService.post(postReq);

            // Update captured unit cost on the line
            line.setUnitCost(unitCostAtPost);
            issueItemRepository.save(line);

            // Release reservation if linked
            if (line.getReservation() != null) {
                StockReservation res = line.getReservation();
                res.setStatus("CONSUMED");
                stockReservationRepository.save(res);
            }

            // Asset assignment for NON_CONSUMABLE / SERIAL items
            boolean isAssetItem = "NON_CONSUMABLE".equalsIgnoreCase(line.getItem().getItemType())
                    && "SERIAL".equalsIgnoreCase(line.getItem().getTrackingType());

            if (isAssetItem) {
                List<UUID> assetIds = lineAssetMap.getOrDefault(line.getId(), List.of());
                for (UUID assetId : assetIds) {
                    Asset asset = assetRepository.findById(assetId)
                            .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND",
                                    "Asset not found: " + assetId, HttpStatus.NOT_FOUND));

                    // Guard: asset must be AVAILABLE
                    if (!"AVAILABLE".equals(asset.getAssetStatus())) {
                        throw new BusinessException("ASSET_NOT_AVAILABLE",
                                "Asset " + asset.getAssetCode() + " is not AVAILABLE. Current status: " + asset.getAssetStatus(),
                                HttpStatus.BAD_REQUEST);
                    }

                    // Guard: cannot have two active assignments
                    assetAssignmentRepository.findFirstByAssetIdAndStatus(assetId, "ACTIVE").ifPresent(existing -> {
                        throw new BusinessException("ASSET_ALREADY_ASSIGNED",
                                "Asset " + asset.getAssetCode() + " already has an active assignment.",
                                HttpStatus.CONFLICT);
                    });

                    // Update asset custodian and status
                    asset.setAssetStatus("ISSUED");
                    asset.setCurrentCustodianUserId(header.getIssuedToUserId());
                    asset.setCurrentDepartmentId(header.getDepartmentId());
                    asset.setCurrentProjectId(header.getProjectId());
                    asset.setIssueItemId(line.getId());
                    asset.setUpdatedBy(currentUserId);
                    asset.setUpdatedAt(Instant.now());
                    assetRepository.save(asset);

                    // Create assignment record
                    AssetAssignment assignment = new AssetAssignment();
                    assignment.setAssetId(assetId);
                    assignment.setAssignmentType(header.getIssuedToType());
                    assignment.setAssigneeUserId(header.getIssuedToUserId());
                    assignment.setAssigneeNameSnapshot(header.getIssuedToNameSnapshot());
                    assignment.setDepartmentId(header.getDepartmentId());
                    assignment.setProjectId(header.getProjectId());
                    assignment.setIssueId(header.getId());
                    assignment.setStatus("ACTIVE");
                    assignment.setCreatedBy(currentUserId);
                    assetAssignmentRepository.save(assignment);

                    assetsAssigned++;
                }
            }

            postedLines++;
        }

        // Transition to POSTED
        header.setStatus("POSTED");
        header.setIssuedByUserId(currentUserId);
        header.setUpdatedBy(currentUserId);
        header.setUpdatedAt(Instant.now());
        issueHeaderRepository.save(header);

        auditService.record(new AuditEvent(
                "ISSUE", "POST", "ISSUE_HEADER", issueId, header.getIssueNo(),
                null, "POSTED", "lines=" + postedLines + " assets=" + assetsAssigned
        ));

        return new IssueDto.PostResult(
                header.getId(),
                header.getIssueNo(),
                "POSTED",
                postedLines,
                assetsAssigned,
                Instant.now()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACKNOWLEDGE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Digital acknowledgement by the recipient after receiving issued items.
     * Transitions to ACKNOWLEDGED or PARTIALLY_ACKNOWLEDGED.
     */
    @Transactional
    public IssueDto.Response acknowledgeIssue(UUID issueId, IssueDto.AcknowledgeRequest request) {
        UUID currentUserId = CurrentUserHolder.getUserId();
        IssueHeader header = findOrThrow(issueId);

        if (!"POSTED".equals(header.getStatus())) {
            throw new BusinessException("INVALID_STATE",
                    "Issue must be POSTED to acknowledge. Current status: " + header.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String newStatus = "ACCEPTED".equals(request.acknowledgementStatus()) ? "ACKNOWLEDGED" : "PARTIALLY_ACKNOWLEDGED";

        header.setAcknowledgedAt(Instant.now());
        header.setAcknowledgedBy(currentUserId);
        header.setAcknowledgementStatus(request.acknowledgementStatus());
        header.setStatus(newStatus);
        header.setUpdatedBy(currentUserId);
        header.setUpdatedAt(Instant.now());

        // Update asset assignment acknowledgement timestamp for all active assignments from this issue
        List<AssetAssignment> assignments = assetAssignmentRepository.findByIssueId(issueId);
        Instant ackTime = Instant.now();
        for (AssetAssignment a : assignments) {
            if ("ACTIVE".equals(a.getStatus())) {
                a.setAcknowledgementAt(ackTime);
                assetAssignmentRepository.save(a);
            }
        }

        auditService.record(new AuditEvent(
                "ISSUE", "ACKNOWLEDGE", "ISSUE_HEADER", issueId, header.getIssueNo(),
                "POSTED", newStatus, request.acknowledgementStatus()
        ));

        return toResponse(issueHeaderRepository.save(header));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public IssueDto.Response getIssue(UUID issueId) {
        return toResponse(findOrThrow(issueId));
    }

    @Transactional(readOnly = true)
    public PageResponse<IssueDto.SummaryResponse> search(UUID storeId, String status, UUID issuedToUserId, Pageable pageable) {
        Page<IssueHeader> page = issueHeaderRepository.search(storeId, status, issuedToUserId, pageable);
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private IssueHeader findOrThrow(UUID issueId) {
        return issueHeaderRepository.findById(issueId)
                .orElseThrow(() -> new BusinessException("ISSUE_NOT_FOUND",
                        "Issue not found: " + issueId, HttpStatus.NOT_FOUND));
    }

    private IssueDto.PostResult buildPostResult(IssueHeader header, UUID currentUserId) {
        List<AssetAssignment> assignments = assetAssignmentRepository.findByIssueId(header.getId());
        return new IssueDto.PostResult(
                header.getId(),
                header.getIssueNo(),
                header.getStatus(),
                header.getItems().size(),
                assignments.size(),
                header.getUpdatedAt()
        );
    }

    public IssueDto.Response toResponse(IssueHeader h) {
        List<IssueDto.ItemResponse> itemResponses = h.getItems().stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return new IssueDto.Response(
                h.getId(),
                h.getIssueNo(),
                h.getIssueDate(),
                h.getRequisition() != null ? h.getRequisition().getId() : null,
                h.getStore().getId(),
                h.getStore().getStoreCode(),
                h.getStore().getStoreName(),
                h.getIssuedToType(),
                h.getIssuedToUserId(),
                h.getIssuedToNameSnapshot(),
                h.getDepartmentId(),
                h.getDepartmentNameSnapshot(),
                h.getProjectId(),
                h.getProjectNameSnapshot(),
                h.getPurpose(),
                h.getStatus(),
                h.getIssuedByUserId(),
                h.getAcknowledgedAt(),
                h.getAcknowledgementStatus(),
                h.getCreatedAt(),
                h.getVersion(),
                itemResponses
        );
    }

    private IssueDto.ItemResponse toItemResponse(IssueItem line) {
        return new IssueDto.ItemResponse(
                line.getId(),
                line.getLineNo(),
                line.getItem().getId(),
                line.getItem().getItemCode(),
                line.getItem().getItemName(),
                line.getLocation().getId(),
                line.getLocation().getLocationCode(),
                line.getLot() != null ? line.getLot().getId() : null,
                line.getLot() != null ? line.getLot().getLotNumber() : null,
                line.getIssueQty(),
                line.getUnitCost(),
                line.getRemarks()
        );
    }

    private IssueDto.SummaryResponse toSummaryResponse(IssueHeader h) {
        return new IssueDto.SummaryResponse(
                h.getId(),
                h.getIssueNo(),
                h.getIssueDate(),
                h.getStore().getId(),
                h.getStore().getStoreCode(),
                h.getIssuedToType(),
                h.getIssuedToNameSnapshot(),
                h.getStatus(),
                h.getItems().size(),
                h.getCreatedAt()
        );
    }
}
