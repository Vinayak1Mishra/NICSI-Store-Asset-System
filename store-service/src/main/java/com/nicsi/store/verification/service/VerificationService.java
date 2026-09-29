package com.nicsi.store.verification.service;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.verification.domain.PhysicalVerification;
import com.nicsi.store.verification.domain.PhysicalVerificationItem;
import com.nicsi.store.verification.dto.VerificationDto;
import com.nicsi.store.verification.repository.PhysicalVerificationRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class VerificationService {

    private final PhysicalVerificationRepository verificationRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final ItemRepository itemRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final DocumentNumberService documentNumberService;

    public VerificationService(PhysicalVerificationRepository verificationRepository,
                               StoreSiteRepository storeSiteRepository,
                               ItemRepository itemRepository,
                               StorageLocationRepository storageLocationRepository,
                               StockBalanceRepository stockBalanceRepository,
                               DocumentNumberService documentNumberService) {
        this.verificationRepository = verificationRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.itemRepository = itemRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public VerificationDto.Response createVerification(VerificationDto.CreateRequest req) {
        PhysicalVerification pv = new PhysicalVerification();
        pv.setVerificationNo(documentNumberService.nextNumber("VERIFICATION", "VER"));
        pv.setVerificationName(req.verificationName());
        pv.setStore(storeSiteRepository.findById(req.storeId())
                .orElseThrow(() -> new BusinessException("Store not found", HttpStatus.NOT_FOUND)));
        pv.setVerificationType(req.verificationType());
        pv.setStartDate(req.startDate());
        pv.setCommitteeReference(req.committeeReference());
        pv.setCreatedBy(CurrentUserHolder.getUserId());

        // Auto-populate count sheet from stock balances
        stockBalanceRepository.findByStoreId(req.storeId()).forEach(sb -> {
            PhysicalVerificationItem item = new PhysicalVerificationItem();
            item.setItem(sb.getItem());
            item.setLocation(sb.getLocation());
            item.setLotId(sb.getLot() != null ? sb.getLot().getId() : null);
            item.setBookQty(sb.getOnHandQty());
            pv.addItem(item);
        });

        return toResponse(verificationRepository.save(pv));
    }

    @Transactional
    public VerificationDto.Response startVerification(UUID id) {
        PhysicalVerification pv = getOrThrow(id);
        if (!"DRAFT".equals(pv.getStatus()) && !"PLANNED".equals(pv.getStatus()))
            throw new BusinessException("Cannot start from status: " + pv.getStatus(), HttpStatus.CONFLICT);
        pv.setStatus("IN_PROGRESS");
        pv.setSnapshotTime(Instant.now());
        return toResponse(verificationRepository.save(pv));
    }

    @Transactional
    public VerificationDto.Response recordCount(UUID id, VerificationDto.RecordCountRequest req) {
        PhysicalVerification pv = getOrThrow(id);
        if (!"IN_PROGRESS".equals(pv.getStatus()))
            throw new BusinessException("Verification must be IN_PROGRESS to record counts", HttpStatus.CONFLICT);

        PhysicalVerificationItem line = pv.getItems().stream()
                .filter(i -> i.getItem().getId().equals(req.itemId())
                        && i.getLocation().getId().equals(req.locationId()))
                .findFirst()
                .orElseGet(() -> {
                    // Allow new items found during count (EXCESS)
                    PhysicalVerificationItem ni = new PhysicalVerificationItem();
                    ni.setItem(itemRepository.findById(req.itemId())
                            .orElseThrow(() -> new BusinessException("Item not found", HttpStatus.NOT_FOUND)));
                    ni.setLocation(storageLocationRepository.findById(req.locationId())
                            .orElseThrow(() -> new BusinessException("Location not found", HttpStatus.NOT_FOUND)));
                    ni.setBookQty(BigDecimal.ZERO);
                    pv.addItem(ni);
                    return ni;
                });

        line.setPhysicalQty(req.physicalQty());
        line.setVarianceQty(req.physicalQty().subtract(line.getBookQty()));
        line.setConditionStatus(req.conditionStatus());
        line.setRemarks(req.remarks());
        line.setScannedAt(Instant.now());
        line.setScannedBy(CurrentUserHolder.getUserId());
        line.setAssetId(req.assetId());

        // Determine result status
        BigDecimal variance = line.getVarianceQty();
        if (variance.compareTo(BigDecimal.ZERO) == 0) line.setResultStatus("FOUND");
        else if (variance.compareTo(BigDecimal.ZERO) < 0) line.setResultStatus("MISSING");
        else line.setResultStatus("EXCESS");

        return toResponse(verificationRepository.save(pv));
    }

    @Transactional
    public VerificationDto.Response submitVerification(UUID id) {
        PhysicalVerification pv = getOrThrow(id);
        if (!"IN_PROGRESS".equals(pv.getStatus()) && !"RECONCILIATION".equals(pv.getStatus()))
            throw new BusinessException("Cannot submit from status: " + pv.getStatus(), HttpStatus.CONFLICT);
        pv.setStatus("SUBMITTED");
        return toResponse(verificationRepository.save(pv));
    }

    @Transactional
    public VerificationDto.Response approveVerification(UUID id) {
        PhysicalVerification pv = getOrThrow(id);
        if (!"SUBMITTED".equals(pv.getStatus()))
            throw new BusinessException("Only SUBMITTED verifications can be approved", HttpStatus.CONFLICT);
        pv.setStatus("APPROVED");
        pv.setApprovedBy(CurrentUserHolder.getUserId());
        pv.setApprovedAt(Instant.now());
        return toResponse(verificationRepository.save(pv));
    }

    @Transactional(readOnly = true)
    public VerificationDto.Response getVerification(UUID id) { return toResponse(getOrThrow(id)); }

    @Transactional(readOnly = true)
    public List<VerificationDto.ItemResponse> getItems(UUID id) {
        return getOrThrow(id).getItems().stream().map(this::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<VerificationDto.Response> search(UUID storeId, String status, Pageable pageable) {
        Specification<PhysicalVerification> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (storeId != null) predicates.add(cb.equal(root.get("store").get("id"), storeId));
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<PhysicalVerification> page = verificationRepository.findAll(spec, pageable);
        List<VerificationDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private PhysicalVerification getOrThrow(UUID id) {
        return verificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Verification not found", HttpStatus.NOT_FOUND));
    }

    private VerificationDto.Response toResponse(PhysicalVerification pv) {
        int counted = (int) pv.getItems().stream().filter(i -> i.getPhysicalQty() != null).count();
        return new VerificationDto.Response(
                pv.getId(), pv.getVerificationNo(), pv.getVerificationName(),
                pv.getStore().getId(), pv.getStore().getStoreName(),
                pv.getVerificationType(), pv.getSnapshotTime(),
                pv.getStartDate(), pv.getEndDate(), pv.getCommitteeReference(), pv.getStatus(),
                pv.getItems().size(), counted, pv.getApprovedAt(), pv.getApprovedBy(),
                pv.getCreatedAt(), pv.getCreatedBy());
    }

    private VerificationDto.ItemResponse toItemResponse(PhysicalVerificationItem i) {
        return new VerificationDto.ItemResponse(
                i.getId(), i.getItem().getId(), i.getItem().getItemCode(), i.getItem().getItemName(),
                i.getLocation().getId(), i.getLocation().getLocationCode(),
                i.getBookQty(), i.getPhysicalQty(), i.getVarianceQty(),
                i.getResultStatus(), i.getConditionStatus(), i.getScannedAt(), i.getRemarks());
    }
}
