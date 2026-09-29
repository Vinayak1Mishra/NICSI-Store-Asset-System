package com.nicsi.store.condemnation.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.condemnation.domain.Condemnation;
import com.nicsi.store.condemnation.domain.CondemnationItem;
import com.nicsi.store.condemnation.dto.CondemnationDto;
import com.nicsi.store.condemnation.repository.CondemnationRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CondemnationService {

    private final CondemnationRepository condemnationRepository;
    private final AssetRepository assetRepository;
    private final DocumentNumberService documentNumberService;

    public CondemnationService(CondemnationRepository condemnationRepository,
                               AssetRepository assetRepository,
                               DocumentNumberService documentNumberService) {
        this.condemnationRepository = condemnationRepository;
        this.assetRepository = assetRepository;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public CondemnationDto.Response create(CondemnationDto.CreateRequest req) {
        Condemnation c = new Condemnation();
        c.setCondemnationNo(documentNumberService.nextNumber("CONDEMNATION", "CND"));
        c.setProposalDate(req.proposalDate() != null ? req.proposalDate() : LocalDate.now());
        c.setCommitteeReference(req.committeeReference());
        c.setTechnicalReason(req.technicalReason());
        c.setCreatedBy(CurrentUserHolder.getUserId());
        c.setStatus("DRAFT");

        for (CondemnationDto.CreateLine l : req.items()) {
            Asset asset = assetRepository.findById(l.assetId())
                    .orElseThrow(() -> new BusinessException("Asset not found: " + l.assetId(), HttpStatus.NOT_FOUND));
            CondemnationItem item = new CondemnationItem();
            item.setAsset(asset);
            item.setAssessedCondition(l.assessedCondition());
            item.setResidualValue(l.residualValue());
            item.setRecommendedMethod(l.recommendedMethod());
            item.setRemarks(l.remarks());
            c.addItem(item);
        }

        return toResponse(condemnationRepository.save(c));
    }

    @Transactional
    public CondemnationDto.Response submit(UUID id) {
        Condemnation c = getOrThrow(id);
        if (!"DRAFT".equals(c.getStatus())) {
            throw new BusinessException("Only DRAFT condemnation can be submitted", HttpStatus.CONFLICT);
        }
        c.setStatus("SUBMITTED");
        return toResponse(condemnationRepository.save(c));
    }

    @Transactional
    public CondemnationDto.Response recommend(UUID id) {
        Condemnation c = getOrThrow(id);
        if (!"SUBMITTED".equals(c.getStatus())) {
            throw new BusinessException("Only SUBMITTED condemnation can be recommended", HttpStatus.CONFLICT);
        }
        c.setStatus("TECHNICALLY_RECOMMENDED");
        return toResponse(condemnationRepository.save(c));
    }

    @Transactional
    public CondemnationDto.Response approve(UUID id) {
        Condemnation c = getOrThrow(id);
        if (!"SUBMITTED".equals(c.getStatus()) && !"TECHNICALLY_RECOMMENDED".equals(c.getStatus())) {
            throw new BusinessException("Condemnation must be SUBMITTED or TECHNICALLY_RECOMMENDED to approve", HttpStatus.CONFLICT);
        }
        c.setStatus("APPROVED");
        c.setApprovedBy(CurrentUserHolder.getUserId());
        c.setApprovedAt(Instant.now());

        // Update asset status to CONDEMNED
        for (CondemnationItem item : c.getItems()) {
            Asset asset = item.getAsset();
            asset.setAssetStatus("CONDEMNED");
            if (item.getAssessedCondition() != null) {
                asset.setConditionStatus(item.getAssessedCondition());
            }
            assetRepository.save(asset);
        }

        return toResponse(condemnationRepository.save(c));
    }

    @Transactional(readOnly = true)
    public CondemnationDto.Response getById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CondemnationDto.Response> search(String status, Pageable pageable) {
        Specification<Condemnation> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Condemnation> page = condemnationRepository.findAll(spec, pageable);
        List<CondemnationDto.Response> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Condemnation getOrThrow(UUID id) {
        return condemnationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Condemnation not found: " + id, HttpStatus.NOT_FOUND));
    }

    private CondemnationDto.Response toResponse(Condemnation c) {
        List<CondemnationDto.ItemResponse> itemResponses = c.getItems().stream().map(i ->
                new CondemnationDto.ItemResponse(
                        i.getId(),
                        i.getAsset().getId(),
                        i.getAsset().getAssetCode(),
                        i.getAsset().getItem() != null ? i.getAsset().getItem().getItemName() : "",
                        i.getAsset().getSerialNumber(),
                        i.getAssessedCondition(),
                        i.getResidualValue(),
                        i.getRecommendedMethod(),
                        i.getRemarks()
                )
        ).toList();

        return new CondemnationDto.Response(
                c.getId(),
                c.getCondemnationNo(),
                c.getProposalDate(),
                c.getCommitteeReference(),
                c.getTechnicalReason(),
                c.getStatus(),
                c.getItems().size(),
                c.getApprovedAt(),
                c.getApprovedBy(),
                c.getCreatedAt(),
                c.getCreatedBy(),
                itemResponses
        );
    }
}
