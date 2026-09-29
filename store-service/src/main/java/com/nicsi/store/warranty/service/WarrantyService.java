package com.nicsi.store.warranty.service;

import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.warranty.domain.SupportContract;
import com.nicsi.store.warranty.dto.WarrantyDto;
import com.nicsi.store.warranty.repository.SupportContractRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WarrantyService {

    private final SupportContractRepository contractRepository;
    private final AssetRepository assetRepository;
    private final ItemRepository itemRepository;

    public WarrantyService(SupportContractRepository contractRepository,
                           AssetRepository assetRepository, ItemRepository itemRepository) {
        this.contractRepository = contractRepository;
        this.assetRepository = assetRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public WarrantyDto.Response create(WarrantyDto.CreateRequest req) {
        SupportContract sc = new SupportContract();
        sc.setContractType(req.contractType());
        sc.setContractNumber(req.contractNumber());
        sc.setVendorNameSnapshot(req.vendorNameSnapshot());
        sc.setStartDate(req.startDate());
        sc.setEndDate(req.endDate());
        sc.setCoverageDetail(req.coverageDetail());
        sc.setSlaDetail(req.slaDetail());
        sc.setAmount(req.amount());
        sc.setRenewalReminderDays(req.renewalReminderDays() != null ? req.renewalReminderDays() : 30);
        sc.setCreatedBy(CurrentUserHolder.getUserId());

        if (req.assetId() != null)
            sc.setAsset(assetRepository.findById(req.assetId())
                    .orElseThrow(() -> new BusinessException("Asset not found", HttpStatus.NOT_FOUND)));
        if (req.itemId() != null)
            sc.setItem(itemRepository.findById(req.itemId())
                    .orElseThrow(() -> new BusinessException("Item not found", HttpStatus.NOT_FOUND)));

        return toResponse(contractRepository.save(sc));
    }

    @Transactional
    public WarrantyDto.Response update(UUID id, WarrantyDto.UpdateRequest req) {
        SupportContract sc = getOrThrow(id);
        if (req.status() != null) sc.setStatus(req.status());
        if (req.endDate() != null) sc.setEndDate(req.endDate());
        if (req.coverageDetail() != null) sc.setCoverageDetail(req.coverageDetail());
        if (req.slaDetail() != null) sc.setSlaDetail(req.slaDetail());
        if (req.amount() != null) sc.setAmount(req.amount());
        sc.setUpdatedBy(CurrentUserHolder.getUserId());
        return toResponse(contractRepository.save(sc));
    }

    @Transactional(readOnly = true)
    public WarrantyDto.Response getById(UUID id) { return toResponse(getOrThrow(id)); }

    @Transactional(readOnly = true)
    public PageResponse<WarrantyDto.Response> search(String type, String status, UUID assetId, Pageable pageable) {
        Specification<SupportContract> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null && !type.isBlank()) predicates.add(cb.equal(root.get("contractType"), type.toUpperCase()));
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            if (assetId != null) predicates.add(cb.equal(root.get("asset").get("id"), assetId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<SupportContract> page = contractRepository.findAll(spec, pageable);
        List<WarrantyDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private SupportContract getOrThrow(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Contract not found", HttpStatus.NOT_FOUND));
    }

    private WarrantyDto.Response toResponse(SupportContract sc) {
        long daysToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), sc.getEndDate());
        return new WarrantyDto.Response(
                sc.getId(), sc.getContractType(), sc.getContractNumber(),
                sc.getVendorNameSnapshot(),
                sc.getItem() != null ? sc.getItem().getId() : null,
                sc.getItem() != null ? sc.getItem().getItemName() : null,
                sc.getAsset() != null ? sc.getAsset().getId() : null,
                sc.getAsset() != null ? sc.getAsset().getAssetCode() : null,
                sc.getStartDate(), sc.getEndDate(),
                sc.getCoverageDetail(), sc.getSlaDetail(), sc.getAmount(),
                sc.getRenewalReminderDays(), sc.getStatus(), daysToExpiry,
                sc.getCreatedAt(), sc.getCreatedBy(), sc.getUpdatedAt());
    }
}
