package com.nicsi.store.grn.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.asset.service.AssetRegistrationService;
import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.rules.MakerCheckerGuard;
import com.nicsi.store.common.rules.MakerCheckerOperation;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.grn.domain.Grn;
import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.inventory.service.InventoryPostingRequest;
import com.nicsi.store.inventory.service.InventoryPostingService;
import com.nicsi.store.master.domain.Item;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GrnPostingService {

    private final GrnRepository grnRepository;
    private final InventoryPostingService inventoryPostingService;
    private final AssetRegistrationService assetRegistrationService;
    private final AssetRepository assetRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final MakerCheckerGuard makerCheckerGuard;
    private final AuditService auditService;

    public GrnPostingService(
            GrnRepository grnRepository,
            InventoryPostingService inventoryPostingService,
            AssetRegistrationService assetRegistrationService,
            AssetRepository assetRepository,
            StockTransactionRepository stockTransactionRepository,
            MakerCheckerGuard makerCheckerGuard,
            AuditService auditService
    ) {
        this.grnRepository = grnRepository;
        this.inventoryPostingService = inventoryPostingService;
        this.assetRegistrationService = assetRegistrationService;
        this.assetRepository = assetRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.makerCheckerGuard = makerCheckerGuard;
        this.auditService = auditService;
    }

    /**
     * Posts accepted items of a GRN to stock ledger and asset register atomically.
     * Executes in a single database transaction (Propagation.REQUIRED).
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public GrnPostDto.PostResponse postGrn(UUID grnId, GrnPostDto.PostRequest request, String idempotencyKey) {
        Grn grn = grnRepository.findById(grnId)
                .orElseThrow(() -> new BusinessException("GRN_NOT_FOUND", "GRN not found with id: " + grnId, HttpStatus.NOT_FOUND));

        // 1. Idempotency replay check: if already posted with this key or already POSTED
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String checkKey = String.format("%s:line-1", idempotencyKey.trim());
            Optional<StockTransaction> existingTxn = stockTransactionRepository.findByIdempotencyKey(checkKey);
            if (existingTxn.isPresent() || "POSTED".equalsIgnoreCase(grn.getStatus())) {
                return replayExistingPostResponse(grn, idempotencyKey);
            }
        } else if ("POSTED".equalsIgnoreCase(grn.getStatus())) {
            return replayExistingPostResponse(grn, idempotencyKey);
        }

        // 2. Status check
        if (!"ACCEPTED".equalsIgnoreCase(grn.getStatus()) && !"PARTIALLY_ACCEPTED".equalsIgnoreCase(grn.getStatus())) {
            throw new BusinessException(
                    "GRN_NOT_POSTABLE",
                    "Only ACCEPTED or PARTIALLY_ACCEPTED GRN can be posted to inventory. Current status: " + grn.getStatus(),
                    HttpStatus.BAD_REQUEST
            );
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        // 3. Maker-Checker check: receiver cannot post to stock
        makerCheckerGuard.assertDifferentUser(grn.getReceivedByUserId(), currentUserId, MakerCheckerOperation.GRN_POST);

        // Map line serials by grnItemId
        Map<UUID, List<String>> lineSerialMap = new HashMap<>();
        if (request != null && request.lineSerials() != null) {
            for (GrnPostDto.LineSerialRequest lsr : request.lineSerials()) {
                lineSerialMap.put(lsr.grnItemId(), lsr.serialNumbers());
            }
        }

        UUID movementGroupId = UUID.randomUUID();
        List<GrnPostDto.PostedLineResponse> postedLines = new ArrayList<>();
        List<UUID> allGeneratedAssetIds = new ArrayList<>();

        // 4. Iterate over GRN items
        for (GrnItem itemLine : grn.getItems()) {
            BigDecimal acceptedQty = itemLine.getAcceptedQty() != null ? itemLine.getAcceptedQty() : BigDecimal.ZERO;
            if (acceptedQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // No accepted quantity to post
            }

            Item item = itemLine.getItem();

            // Point 5: LICENSE-tracked / SOFTWARE items do NOT post to stock_balance or physical ledger
            boolean isSoftwareLicense = "SOFTWARE".equalsIgnoreCase(item.getItemType()) || "LICENSE".equalsIgnoreCase(item.getTrackingType());
            if (isSoftwareLicense) {
                // Record line metadata as pending license intake, but do not post to physical ledger
                itemLine.setRemarks((itemLine.getRemarks() != null ? itemLine.getRemarks() + " | " : "") + "PENDING_LICENSE_ENTITLEMENT_INTAKE");
                postedLines.add(new GrnPostDto.PostedLineResponse(
                        itemLine.getId(),
                        item.getId(),
                        item.getItemCode(),
                        item.getItemName(),
                        acceptedQty,
                        itemLine.getUnitRate(),
                        itemLine.getReceivingLocation().getLocationCode(),
                        "DEFERRED_LICENSE_INTAKE",
                        0
                ));
                continue;
            }

            // Derive deterministic line idempotency key: {header-key}:line-{line-no}
            String lineKey = idempotencyKey != null && !idempotencyKey.isBlank()
                    ? String.format("%s:line-%d", idempotencyKey.trim(), itemLine.getLineNo())
                    : null;

            // Post to inventory ledger & balance
            InventoryPostingRequest postingReq = InventoryPostingRequest.builder()
                    .transactionType("RECEIPT")
                    .item(item)
                    .store(grn.getStore())
                    .location(itemLine.getReceivingLocation())
                    .lotNumber(itemLine.getBatchLotNo())
                    .manufactureDate(itemLine.getManufactureDate())
                    .expiryDate(itemLine.getExpiryDate())
                    .quantityIn(acceptedQty)
                    .unitCost(itemLine.getUnitRate() != null ? itemLine.getUnitRate() : BigDecimal.ZERO)
                    .referenceType("GRN")
                    .referenceId(grn.getId())
                    .referenceNo(grn.getGrnNo())
                    .movementGroupId(movementGroupId)
                    .idempotencyKey(lineKey)
                    .remarks(request != null ? request.remarks() : null)
                    .postedBy(currentUserId)
                    .build();

            StockTransaction st = inventoryPostingService.post(postingReq);

            // Asset registration for serialised items
            int assetsCreatedCount = 0;
            boolean isSerialOrAsset = "SERIAL".equalsIgnoreCase(item.getTrackingType()) || item.isAssetRequired();
            if (isSerialOrAsset) {
                List<String> serials = lineSerialMap.get(itemLine.getId());
                List<Asset> createdAssets = assetRegistrationService.registerAssetsFromGrn(itemLine, serials);
                assetsCreatedCount = createdAssets.size();
                for (Asset a : createdAssets) {
                    allGeneratedAssetIds.add(a.getId());
                }
            }

            postedLines.add(new GrnPostDto.PostedLineResponse(
                    itemLine.getId(),
                    item.getId(),
                    item.getItemCode(),
                    item.getItemName(),
                    acceptedQty,
                    itemLine.getUnitRate(),
                    itemLine.getReceivingLocation().getLocationCode(),
                    st != null ? st.getTransactionNo() : null,
                    assetsCreatedCount
            ));
        }

        // 5. Update GRN status
        String oldStatus = grn.getStatus();
        grn.setStatus("POSTED");
        grn.setApprovedBy(currentUserId);
        grn.setUpdatedBy(currentUserId);
        grn.setApprovedAt(nowTruncatedToMicros());
        Grn savedGrn = grnRepository.save(grn);

        // 6. Audit event
        auditService.record(new AuditEvent(
                "STORE", "POST", "GRN",
                savedGrn.getId(), savedGrn.getGrnNo(), oldStatus, "POSTED",
                "GRN posted to inventory ledger. Lines posted: " + postedLines.size() + ", Assets created: " + allGeneratedAssetIds.size()
        ));

        return new GrnPostDto.PostResponse(
                savedGrn.getId(),
                savedGrn.getGrnNo(),
                savedGrn.getStatus(),
                savedGrn.getApprovedAt(),
                currentUserId,
                postedLines.size(),
                allGeneratedAssetIds.size(),
                postedLines,
                allGeneratedAssetIds
        );
    }

    /**
     * "Now" truncated to the precision PostgreSQL actually stores.
     *
     * A timestamptz column keeps microseconds, but Instant.now() carries nanoseconds. The response
     * of a live post is built from the in-memory entity (nanoseconds) while an idempotent replay is
     * rebuilt from the reloaded row (microseconds), so without this the same request returns two
     * different timestamps and the replay is not body-identical. Truncating at the point of
     * ASSIGNMENT makes the in-memory value and the stored value identical, so both paths agree.
     *
     * Note: store.grn has no posted_at column, so the posting time is recorded in approved_at
     * (see the assignment above). PostResponse.postedAt is read back from that same field on both
     * the live and the replay path, which is what makes them agree.
     */
    private static Instant nowTruncatedToMicros() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private GrnPostDto.PostResponse replayExistingPostResponse(Grn grn, String idempotencyKey) {
        List<StockTransaction> txns = stockTransactionRepository.findByReferenceTypeAndReferenceId("GRN", grn.getId());
        Map<UUID, StockTransaction> itemTxnMap = new HashMap<>();
        for (StockTransaction st : txns) {
            itemTxnMap.put(st.getItem().getId(), st);
        }

        List<GrnPostDto.PostedLineResponse> postedLines = new ArrayList<>();
        List<UUID> allGeneratedAssetIds = new ArrayList<>();

        for (GrnItem line : grn.getItems()) {
            BigDecimal accepted = line.getAcceptedQty() != null ? line.getAcceptedQty() : BigDecimal.ZERO;
            if (accepted.compareTo(BigDecimal.ZERO) <= 0) continue;

            StockTransaction st = itemTxnMap.get(line.getItem().getId());
            List<Asset> assets = assetRepository.findByGrnItemId(line.getId());
            for (Asset a : assets) {
                allGeneratedAssetIds.add(a.getId());
            }

            postedLines.add(new GrnPostDto.PostedLineResponse(
                    line.getId(),
                    line.getItem().getId(),
                    line.getItem().getItemCode(),
                    line.getItem().getItemName(),
                    accepted,
                    line.getUnitRate(),
                    line.getReceivingLocation().getLocationCode(),
                    st != null ? st.getTransactionNo() : (assets.isEmpty() ? "DEFERRED_LICENSE_INTAKE" : null),
                    assets.size()
            ));
        }

        return new GrnPostDto.PostResponse(
                grn.getId(),
                grn.getGrnNo(),
                grn.getStatus(),
                grn.getApprovedAt() != null ? grn.getApprovedAt() : Instant.now(),
                grn.getApprovedBy() != null ? grn.getApprovedBy() : grn.getReceivedByUserId(),
                postedLines.size(),
                allGeneratedAssetIds.size(),
                postedLines,
                allGeneratedAssetIds
        );
    }
}
