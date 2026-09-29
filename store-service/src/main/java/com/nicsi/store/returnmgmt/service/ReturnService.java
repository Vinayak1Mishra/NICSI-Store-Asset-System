package com.nicsi.store.returnmgmt.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.service.InventoryPostingRequest;
import com.nicsi.store.inventory.service.InventoryPostingService;
import com.nicsi.store.issue.domain.AssetAssignment;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.returnmgmt.domain.ReturnHeader;
import com.nicsi.store.returnmgmt.domain.ReturnItem;
import com.nicsi.store.returnmgmt.dto.ReturnDto;
import com.nicsi.store.returnmgmt.repository.ReturnHeaderRepository;
import com.nicsi.store.returnmgmt.repository.ReturnItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReturnService {

    private final ReturnHeaderRepository returnHeaderRepository;
    private final ReturnItemRepository returnItemRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final ItemRepository itemRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final InventoryPostingService inventoryPostingService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public ReturnService(
            ReturnHeaderRepository returnHeaderRepository,
            ReturnItemRepository returnItemRepository,
            StoreSiteRepository storeSiteRepository,
            ItemRepository itemRepository,
            StorageLocationRepository storageLocationRepository,
            AssetRepository assetRepository,
            AssetAssignmentRepository assetAssignmentRepository,
            InventoryPostingService inventoryPostingService,
            DocumentNumberService documentNumberService,
            AuditService auditService
    ) {
        this.returnHeaderRepository = returnHeaderRepository;
        this.returnItemRepository = returnItemRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.itemRepository = itemRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.assetRepository = assetRepository;
        this.assetAssignmentRepository = assetAssignmentRepository;
        this.inventoryPostingService = inventoryPostingService;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    private UUID resolveCurrentUserId() {
        try {
            return CurrentUserHolder.getUserId();
        } catch (Exception e) {
            return UUID.fromString("11111111-1111-1111-1111-111111111111");
        }
    }

    @Transactional
    public ReturnDto.Response createReturn(ReturnDto.CreateRequest request) {
        StoreSite store = storeSiteRepository.findById(request.storeId())
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND", "Store site not found", HttpStatus.NOT_FOUND));

        LocalDate returnDate = request.returnDate() != null ? request.returnDate() : LocalDate.now();
        String returnNo = documentNumberService.nextNumber("RETURN", "RET", returnDate);

        ReturnHeader header = new ReturnHeader();
        header.setReturnNo(returnNo);
        header.setReturnDate(returnDate);
        header.setStore(store);
        header.setReturnedByUserId(request.returnedByUserId());
        header.setDepartmentId(request.departmentId());
        header.setProjectId(request.projectId());
        header.setRemarks(request.remarks());
        header.setStatus("DRAFT");
        header.setCreatedBy(resolveCurrentUserId());

        int lineNum = 1;
        for (ReturnDto.LineRequest lineReq : request.items()) {
            Item item = itemRepository.findById(lineReq.itemId())
                    .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found: " + lineReq.itemId(), HttpStatus.NOT_FOUND));

            StorageLocation location = storageLocationRepository.findById(lineReq.returnLocationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Storage location not found: " + lineReq.returnLocationId(), HttpStatus.NOT_FOUND));

            Asset asset = null;
            if (lineReq.assetId() != null) {
                asset = assetRepository.findById(lineReq.assetId())
                        .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found: " + lineReq.assetId(), HttpStatus.NOT_FOUND));
            }

            ReturnItem itemEntity = new ReturnItem();
            itemEntity.setLineNo(lineNum++);
            itemEntity.setItem(item);
            itemEntity.setAsset(asset);
            itemEntity.setReturnQty(lineReq.returnQty());
            itemEntity.setReturnLocation(location);
            itemEntity.setConditionStatus(lineReq.conditionStatus() != null ? lineReq.conditionStatus() : "GOOD");
            itemEntity.setRemarks(lineReq.remarks());

            header.addItem(itemEntity);
        }

        ReturnHeader saved = returnHeaderRepository.save(header);

        auditService.record(AuditEvent.of("RETURN", "CREATE", "ReturnHeader", saved.getId(), saved.getReturnNo()));

        return toResponse(saved);
    }

    @Transactional
    public ReturnDto.Response submitReturn(UUID id) {
        ReturnHeader header = returnHeaderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("RETURN_NOT_FOUND", "Return not found", HttpStatus.NOT_FOUND));

        if (!"DRAFT".equals(header.getStatus())) {
            throw new BusinessException("INVALID_STATE", "Only DRAFT returns can be submitted", HttpStatus.BAD_REQUEST);
        }

        header.setStatus("SUBMITTED");
        header.setUpdatedBy(resolveCurrentUserId());
        ReturnHeader saved = returnHeaderRepository.save(header);

        auditService.record(AuditEvent.of("RETURN", "SUBMIT", "ReturnHeader", saved.getId(), saved.getReturnNo()));
        return toResponse(saved);
    }

    @Transactional
    public ReturnDto.Response receiveAndInspectReturn(UUID id, ReturnDto.ReceiveRequest request) {
        ReturnHeader header = returnHeaderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("RETURN_NOT_FOUND", "Return not found", HttpStatus.NOT_FOUND));

        if (!"SUBMITTED".equals(header.getStatus()) && !"DRAFT".equals(header.getStatus())) {
            throw new BusinessException("INVALID_STATE", "Return cannot be inspected in status: " + header.getStatus(), HttpStatus.BAD_REQUEST);
        }

        header.setReceivedByUserId(request.receivedByUserId() != null ? request.receivedByUserId() : resolveCurrentUserId());

        if (request.lines() != null) {
            Map<UUID, ReturnDto.ReceiveLineRequest> lineMap = new HashMap<>();
            for (ReturnDto.ReceiveLineRequest l : request.lines()) {
                lineMap.put(l.lineId(), l);
            }

            for (ReturnItem item : header.getItems()) {
                ReturnDto.ReceiveLineRequest update = lineMap.get(item.getId());
                if (update != null) {
                    if (update.conditionStatus() != null) item.setConditionStatus(update.conditionStatus());
                    if (update.disposition() != null) item.setDisposition(update.disposition());
                    if (update.returnLocationId() != null) {
                        StorageLocation loc = storageLocationRepository.findById(update.returnLocationId())
                                .orElse(item.getReturnLocation());
                        item.setReturnLocation(loc);
                    }
                    if (update.remarks() != null) item.setRemarks(update.remarks());
                } else if (item.getDisposition() == null) {
                    item.setDisposition("RESTOCK");
                }
            }
        }

        header.setStatus("INSPECTED");
        header.setUpdatedBy(resolveCurrentUserId());
        ReturnHeader saved = returnHeaderRepository.save(header);

        auditService.record(AuditEvent.of("RETURN", "INSPECT", "ReturnHeader", saved.getId(), saved.getReturnNo()));
        return toResponse(saved);
    }

    @Transactional
    public ReturnDto.Response postReturn(UUID id, ReturnDto.PostRequest request, String idempotencyKey) {
        ReturnHeader header = returnHeaderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("RETURN_NOT_FOUND", "Return not found", HttpStatus.NOT_FOUND));

        if ("POSTED".equals(header.getStatus())) {
            return toResponse(header);
        }

        if (!"INSPECTED".equals(header.getStatus()) && !"SUBMITTED".equals(header.getStatus())) {
            throw new BusinessException("INVALID_STATE", "Return must be INSPECTED before posting", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = resolveCurrentUserId();

        for (ReturnItem line : header.getItems()) {
            String disposition = line.getDisposition() != null ? line.getDisposition().toUpperCase() : "RESTOCK";

            if ("RESTOCK".equals(disposition)) {
                // Post into inventory balance
                InventoryPostingRequest postingReq = InventoryPostingRequest.builder()
                        .item(line.getItem())
                        .store(header.getStore())
                        .location(line.getReturnLocation())
                        .quantityIn(line.getReturnQty())
                        .transactionType("RETURN")
                        .referenceType("RETURN")
                        .referenceId(header.getId())
                        .referenceNo(header.getReturnNo())
                        .remarks("Return " + header.getReturnNo() + " Line " + line.getLineNo())
                        .idempotencyKey(idempotencyKey != null ? idempotencyKey + "-line-" + line.getLineNo() : null)
                        .build();

                inventoryPostingService.post(postingReq);

                if (line.getAsset() != null) {
                    Asset asset = line.getAsset();
                    asset.setAssetStatus("AVAILABLE");
                    if (line.getConditionStatus() != null) asset.setConditionStatus(line.getConditionStatus());
                    asset.setStore(header.getStore());
                    asset.setLocation(line.getReturnLocation());
                    asset.setCurrentCustodianUserId(null);
                    asset.setCurrentDepartmentId(null);
                    asset.setCurrentProjectId(null);
                    assetRepository.save(asset);

                    Optional<AssetAssignment> activeAssign = assetAssignmentRepository.findFirstByAssetIdAndStatus(asset.getId(), "ACTIVE");
                    activeAssign.ifPresent(a -> {
                        a.setStatus("RETURNED");
                        a.setAssignedUntil(Instant.now());
                        assetAssignmentRepository.save(a);
                    });
                }
            } else if ("REPAIR".equals(disposition)) {
                if (line.getAsset() != null) {
                    Asset asset = line.getAsset();
                    asset.setAssetStatus("IN_REPAIR");
                    if (line.getConditionStatus() != null) asset.setConditionStatus(line.getConditionStatus());
                    asset.setCurrentCustodianUserId(null);
                    assetRepository.save(asset);

                    assetAssignmentRepository.findFirstByAssetIdAndStatus(asset.getId(), "ACTIVE").ifPresent(a -> {
                        a.setStatus("RETURNED");
                        a.setAssignedUntil(Instant.now());
                        assetAssignmentRepository.save(a);
                    });
                }
            } else if ("CONDEMNATION".equals(disposition) || "SCRAP".equals(disposition)) {
                if (line.getAsset() != null) {
                    Asset asset = line.getAsset();
                    asset.setAssetStatus("CONDEMNED");
                    if (line.getConditionStatus() != null) asset.setConditionStatus(line.getConditionStatus());
                    asset.setCurrentCustodianUserId(null);
                    assetRepository.save(asset);

                    assetAssignmentRepository.findFirstByAssetIdAndStatus(asset.getId(), "ACTIVE").ifPresent(a -> {
                        a.setStatus("RETURNED");
                        a.setAssignedUntil(Instant.now());
                        assetAssignmentRepository.save(a);
                    });
                }
            } else if ("QUARANTINE".equals(disposition)) {
                if (line.getAsset() != null) {
                    Asset asset = line.getAsset();
                    asset.setAssetStatus("QUARANTINE");
                    assetRepository.save(asset);
                }
            }
        }

        header.setStatus("POSTED");
        header.setUpdatedBy(currentUserId);
        ReturnHeader saved = returnHeaderRepository.save(header);

        auditService.record(AuditEvent.of("RETURN", "POST", "ReturnHeader", saved.getId(), saved.getReturnNo()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReturnDto.Response getReturn(UUID id) {
        ReturnHeader header = returnHeaderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("RETURN_NOT_FOUND", "Return not found", HttpStatus.NOT_FOUND));
        return toResponse(header);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnDto.SummaryResponse> search(UUID storeId, String status, String search, Pageable pageable) {
        final String cleanStatus = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? status.trim().toUpperCase() : null;
        final String cleanSearch = (search != null && !search.isBlank()) ? search.trim().toLowerCase() : null;

        Specification<ReturnHeader> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (storeId != null) {
                predicates.add(cb.equal(root.get("store").get("id"), storeId));
            }
            if (cleanStatus != null) {
                predicates.add(cb.equal(root.get("status"), cleanStatus));
            }
            if (cleanSearch != null) {
                String like = "%" + cleanSearch + "%";
                Predicate byNo = cb.like(cb.lower(root.get("returnNo")), like);
                Predicate byRemarks = cb.like(
                        cb.lower(cb.coalesce(root.get("remarks"), "")), like);
                predicates.add(cb.or(byNo, byRemarks));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ReturnHeader> page = returnHeaderRepository.findAll(spec, pageable);
        List<ReturnDto.SummaryResponse> summaries = page.getContent().stream()
                .map(r -> new ReturnDto.SummaryResponse(
                        r.getId(),
                        r.getReturnNo(),
                        r.getReturnDate(),
                        r.getStore().getId(),
                        r.getStore().getStoreName(),
                        r.getReturnedByUserId(),
                        r.getDepartmentId(),
                        r.getProjectId(),
                        r.getStatus(),
                        r.getItems().size(),
                        r.getCreatedAt()
                ))
                .toList();

        return new PageResponse<>(summaries, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private ReturnDto.Response toResponse(ReturnHeader header) {
        List<ReturnDto.ItemResponse> itemResponses = header.getItems().stream()
                .map(item -> new ReturnDto.ItemResponse(
                        item.getId(),
                        item.getLineNo(),
                        item.getItem().getId(),
                        item.getItem().getItemCode(),
                        item.getItem().getItemName(),
                        item.getAsset() != null ? item.getAsset().getId() : null,
                        item.getAsset() != null ? item.getAsset().getAssetCode() : null,
                        item.getReturnQty(),
                        item.getReturnLocation().getId(),
                        item.getReturnLocation().getLocationCode(),
                        item.getConditionStatus(),
                        item.getDisposition(),
                        item.getRemarks()
                ))
                .toList();

        return new ReturnDto.Response(
                header.getId(),
                header.getReturnNo(),
                header.getReturnDate(),
                header.getStore().getId(),
                header.getStore().getStoreCode(),
                header.getStore().getStoreName(),
                header.getReturnedByUserId(),
                header.getDepartmentId(),
                header.getProjectId(),
                header.getReceivedByUserId(),
                header.getStatus(),
                header.getRemarks(),
                itemResponses,
                header.getCreatedAt(),
                header.getCreatedBy(),
                header.getUpdatedAt(),
                header.getVersion()
        );
    }
}
