package com.nicsi.store.disposal.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.asset.domain.Disposal;
import com.nicsi.store.asset.domain.DisposalItem;
import com.nicsi.store.asset.repository.DisposalRepository;
import com.nicsi.store.disposal.dto.DisposalDto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DisposalService {

    private final DisposalRepository disposalRepository;
    private final AssetRepository assetRepository;
    private final DocumentNumberService documentNumberService;

    public DisposalService(DisposalRepository disposalRepository,
                           AssetRepository assetRepository,
                           DocumentNumberService documentNumberService) {
        this.disposalRepository = disposalRepository;
        this.assetRepository = assetRepository;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public DisposalDto.Response create(DisposalDto.CreateRequest req) {
        Disposal d = new Disposal();
        d.setDisposalNo(documentNumberService.nextNumber("DISPOSAL", "DSP"));
        d.setDisposalDate(req.disposalDate() != null ? req.disposalDate() : LocalDate.now());
        d.setDisposalMethod(req.disposalMethod());
        d.setPurchaserVendorId(req.purchaserVendorId());
        d.setPurchaserNameSnapshot(req.purchaserNameSnapshot());
        d.setSaleAmount(req.saleAmount() != null ? req.saleAmount() : BigDecimal.ZERO);
        d.setCertificateNumber(req.certificateNumber());
        d.setStatus("DRAFT");
        d.setCreatedBy(CurrentUserHolder.getUserId());

        for (DisposalDto.CreateLine l : req.items()) {
            Asset asset = assetRepository.findById(l.assetId())
                    .orElseThrow(() -> new BusinessException("Asset not found: " + l.assetId(), HttpStatus.NOT_FOUND));
            DisposalItem item = new DisposalItem();
            item.setAsset(asset);
            item.setRealizedValue(l.realizedValue() != null ? l.realizedValue() : BigDecimal.ZERO);
            item.setRemarks(l.remarks());
            d.addItem(item);
        }

        return toResponse(disposalRepository.save(d));
    }

    @Transactional
    public DisposalDto.Response submit(UUID id) {
        Disposal d = getOrThrow(id);
        if (!"DRAFT".equals(d.getStatus())) {
            throw new BusinessException("Only DRAFT disposal can be submitted", HttpStatus.CONFLICT);
        }
        d.setStatus("SUBMITTED");
        return toResponse(disposalRepository.save(d));
    }

    @Transactional
    public DisposalDto.Response approve(UUID id) {
        Disposal d = getOrThrow(id);
        if (!"SUBMITTED".equals(d.getStatus())) {
            throw new BusinessException("Only SUBMITTED disposal can be approved", HttpStatus.CONFLICT);
        }
        d.setStatus("APPROVED");
        d.setApprovedBy(CurrentUserHolder.getUserId());
        d.setApprovedAt(Instant.now());
        return toResponse(disposalRepository.save(d));
    }

    @Transactional
    public DisposalDto.Response post(UUID id) {
        Disposal d = getOrThrow(id);
        if (!"APPROVED".equals(d.getStatus())) {
            throw new BusinessException("Only APPROVED disposal can be posted", HttpStatus.CONFLICT);
        }
        d.setStatus("POSTED");
        d.setPostedBy(CurrentUserHolder.getUserId());
        d.setPostedAt(Instant.now());

        // Update asset statuses to DISPOSED
        for (DisposalItem item : d.getItems()) {
            Asset asset = item.getAsset();
            asset.setAssetStatus("DISPOSED");
            asset.setConditionStatus("DISPOSED");
            assetRepository.save(asset);
        }

        return toResponse(disposalRepository.save(d));
    }

    @Transactional(readOnly = true)
    public DisposalDto.Response getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DisposalDto.Response> search(String status, String method, Pageable pageable) {
        Specification<Disposal> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            }
            if (method != null && !method.isBlank() && !"ALL".equalsIgnoreCase(method)) {
                predicates.add(cb.equal(root.get("disposalMethod"), method.toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Disposal> page = disposalRepository.findAll(spec, pageable);
        List<DisposalDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Disposal getOrThrow(UUID id) {
        return disposalRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Disposal not found: " + id, HttpStatus.NOT_FOUND));
    }

    private DisposalDto.Response toResponse(Disposal d) {
        List<DisposalDto.ItemResponse> itemResponses = d.getItems().stream().map(i ->
                new DisposalDto.ItemResponse(
                        i.getId(),
                        i.getAsset().getId(),
                        i.getAsset().getAssetCode(),
                        i.getAsset().getItem() != null ? i.getAsset().getItem().getItemName() : "",
                        i.getAsset().getSerialNumber(),
                        i.getRealizedValue(),
                        i.getRemarks()
                )
        ).toList();

        return new DisposalDto.Response(
                d.getId(),
                d.getDisposalNo(),
                d.getDisposalDate(),
                d.getDisposalMethod(),
                d.getPurchaserVendorId(),
                d.getPurchaserNameSnapshot(),
                d.getSaleAmount(),
                d.getCertificateNumber(),
                d.getStatus(),
                d.getItems().size(),
                d.getApprovedAt(),
                d.getApprovedBy(),
                d.getPostedAt(),
                d.getPostedBy(),
                d.getCreatedAt(),
                d.getCreatedBy(),
                itemResponses
        );
    }
}
