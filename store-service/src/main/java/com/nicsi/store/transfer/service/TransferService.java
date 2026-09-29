package com.nicsi.store.transfer.service;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.service.InventoryPostingRequest;
import com.nicsi.store.inventory.service.InventoryPostingService;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.transfer.domain.TransferHeader;
import com.nicsi.store.transfer.domain.TransferItem;
import com.nicsi.store.transfer.dto.TransferDto;
import com.nicsi.store.transfer.repository.TransferHeaderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final TransferHeaderRepository transferHeaderRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final ItemRepository itemRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final InventoryPostingService inventoryPostingService;
    private final DocumentNumberService documentNumberService;

    public TransferService(TransferHeaderRepository transferHeaderRepository,
                           StoreSiteRepository storeSiteRepository,
                           ItemRepository itemRepository,
                           StorageLocationRepository storageLocationRepository,
                           InventoryPostingService inventoryPostingService,
                           DocumentNumberService documentNumberService) {
        this.transferHeaderRepository = transferHeaderRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.itemRepository = itemRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.inventoryPostingService = inventoryPostingService;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public TransferDto.Response createTransfer(TransferDto.CreateRequest req) {
        if (req.sourceStoreId().equals(req.destinationStoreId()))
            throw new BusinessException("Source and destination stores must differ", HttpStatus.BAD_REQUEST);

        StoreSite source = storeSiteRepository.findById(req.sourceStoreId())
                .orElseThrow(() -> new BusinessException("Source store not found", HttpStatus.NOT_FOUND));
        StoreSite dest = storeSiteRepository.findById(req.destinationStoreId())
                .orElseThrow(() -> new BusinessException("Destination store not found", HttpStatus.NOT_FOUND));

        TransferHeader header = new TransferHeader();
        header.setTransferNo(documentNumberService.nextNumber("TRANSFER", "TRF"));
        header.setSourceStore(source);
        header.setDestinationStore(dest);
        header.setRequestedByUserId(CurrentUserHolder.getUserId());
        header.setCreatedBy(CurrentUserHolder.getUserId());
        header.setRemarks(req.remarks());

        int lineNo = 1;
        for (TransferDto.ItemLine line : req.items()) {
            TransferItem item = new TransferItem();
            item.setLineNo(lineNo++);
            item.setItem(itemRepository.findById(line.itemId())
                    .orElseThrow(() -> new BusinessException("Item not found: " + line.itemId(), HttpStatus.NOT_FOUND)));
            item.setSourceLocation(storageLocationRepository.findById(line.sourceLocationId())
                    .orElseThrow(() -> new BusinessException("Source location not found", HttpStatus.NOT_FOUND)));
            item.setDestinationLocation(storageLocationRepository.findById(line.destinationLocationId())
                    .orElseThrow(() -> new BusinessException("Destination location not found", HttpStatus.NOT_FOUND)));
            item.setTransferQty(line.transferQty());
            item.setAssetId(line.assetId());
            item.setLotId(line.lotId());
            item.setRemarks(line.remarks());
            header.addItem(item);
        }

        return toResponse(transferHeaderRepository.save(header));
    }

    @Transactional
    public TransferDto.Response submitTransfer(UUID id) {
        TransferHeader header = getOrThrow(id);
        if (!"DRAFT".equals(header.getStatus()))
            throw new BusinessException("Only DRAFT transfers can be submitted", HttpStatus.CONFLICT);
        header.setStatus("SUBMITTED");
        header.setUpdatedBy(CurrentUserHolder.getUserId());
        return toResponse(transferHeaderRepository.save(header));
    }

    @Transactional
    public TransferDto.Response approveTransfer(UUID id) {
        TransferHeader header = getOrThrow(id);
        if (!"SUBMITTED".equals(header.getStatus()))
            throw new BusinessException("Only SUBMITTED transfers can be approved", HttpStatus.CONFLICT);
        header.setStatus("APPROVED");
        header.setApprovedByUserId(CurrentUserHolder.getUserId());
        header.setUpdatedBy(CurrentUserHolder.getUserId());
        return toResponse(transferHeaderRepository.save(header));
    }

    @Transactional
    public TransferDto.Response dispatchTransfer(UUID id) {
        TransferHeader header = getOrThrow(id);
        if (!"APPROVED".equals(header.getStatus()))
            throw new BusinessException("Only APPROVED transfers can be dispatched", HttpStatus.CONFLICT);

        // Post stock OUT from source store
        for (TransferItem line : header.getItems()) {
            inventoryPostingService.post(InventoryPostingRequest.builder()
                    .transactionType("TRANSFER_OUT")
                    .referenceType("TRANSFER")
                    .referenceId(header.getId())
                    .referenceNo(header.getTransferNo())
                    .item(line.getItem())
                    .store(header.getSourceStore())
                    .location(line.getSourceLocation())
                    .quantityOut(line.getTransferQty())
                    .unitCost(java.math.BigDecimal.ZERO)
                    .postedBy(CurrentUserHolder.getUserId())
                    .build());
        }

        header.setStatus("DISPATCHED");
        header.setDispatchDate(java.time.LocalDate.now());
        header.setUpdatedBy(CurrentUserHolder.getUserId());
        return toResponse(transferHeaderRepository.save(header));
    }

    @Transactional
    public TransferDto.Response receiveTransfer(UUID id, TransferDto.ReceiveRequest req) {
        TransferHeader header = getOrThrow(id);
        if (!"DISPATCHED".equals(header.getStatus()) && !"IN_TRANSIT".equals(header.getStatus()))
            throw new BusinessException("Transfer must be DISPATCHED or IN_TRANSIT to receive", HttpStatus.CONFLICT);

        // Update received quantities and post IN to destination
        for (TransferDto.ReceivedLine rl : req.lines()) {
            TransferItem line = header.getItems().stream()
                    .filter(i -> i.getId().equals(rl.lineId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Line not found: " + rl.lineId(), HttpStatus.NOT_FOUND));
            line.setReceivedQty(rl.receivedQty());

            inventoryPostingService.post(InventoryPostingRequest.builder()
                    .transactionType("TRANSFER_IN")
                    .referenceType("TRANSFER")
                    .referenceId(header.getId())
                    .referenceNo(header.getTransferNo())
                    .item(line.getItem())
                    .store(header.getDestinationStore())
                    .location(line.getDestinationLocation())
                    .quantityIn(rl.receivedQty())
                    .unitCost(java.math.BigDecimal.ZERO)
                    .postedBy(CurrentUserHolder.getUserId())
                    .build());
        }

        header.setStatus("RECEIVED");
        header.setReceiveDate(java.time.LocalDate.now());
        header.setUpdatedBy(CurrentUserHolder.getUserId());
        return toResponse(transferHeaderRepository.save(header));
    }

    @Transactional(readOnly = true)
    public TransferDto.Response getTransfer(UUID id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferDto.SummaryResponse> search(UUID sourceStoreId, UUID destStoreId,
                                                             String status, Pageable pageable) {
        Specification<TransferHeader> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sourceStoreId != null) predicates.add(cb.equal(root.get("sourceStore").get("id"), sourceStoreId));
            if (destStoreId != null) predicates.add(cb.equal(root.get("destinationStore").get("id"), destStoreId));
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TransferHeader> page = transferHeaderRepository.findAll(spec, pageable);
        List<TransferDto.SummaryResponse> content = page.getContent().stream()
                .map(h -> new TransferDto.SummaryResponse(
                        h.getId(), h.getTransferNo(), h.getTransferDate(),
                        h.getSourceStore().getId(), h.getSourceStore().getStoreName(),
                        h.getDestinationStore().getId(), h.getDestinationStore().getStoreName(),
                        h.getStatus(), h.getItems().size(), h.getCreatedAt()))
                .toList();

        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private TransferHeader getOrThrow(UUID id) {
        return transferHeaderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Transfer not found: " + id, HttpStatus.NOT_FOUND));
    }

    private TransferDto.Response toResponse(TransferHeader h) {
        List<TransferDto.ItemResponse> items = h.getItems().stream()
                .map(i -> new TransferDto.ItemResponse(
                        i.getId(), i.getLineNo(),
                        i.getItem().getId(), i.getItem().getItemCode(), i.getItem().getItemName(),
                        i.getSourceLocation().getId(), i.getSourceLocation().getLocationCode(),
                        i.getDestinationLocation().getId(), i.getDestinationLocation().getLocationCode(),
                        i.getTransferQty(), i.getReceivedQty(), i.getRemarks()))
                .toList();

        return new TransferDto.Response(
                h.getId(), h.getTransferNo(), h.getTransferDate(),
                h.getSourceStore().getId(), h.getSourceStore().getStoreName(),
                h.getDestinationStore().getId(), h.getDestinationStore().getStoreName(),
                h.getRequestedByUserId(), h.getApprovedByUserId(),
                h.getStatus(), h.getDispatchDate(), h.getReceiveDate(),
                h.getRemarks(), items, h.getCreatedAt(), h.getCreatedBy(),
                h.getUpdatedAt(), h.getVersion());
    }
}
