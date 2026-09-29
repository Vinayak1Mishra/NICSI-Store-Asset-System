package com.nicsi.store.repair.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.asset.domain.RepairTicket;
import com.nicsi.store.repair.dto.RepairDto;
import com.nicsi.store.asset.repository.RepairTicketRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RepairService {

    private final RepairTicketRepository repairTicketRepository;
    private final AssetRepository assetRepository;
    private final DocumentNumberService documentNumberService;

    public RepairService(RepairTicketRepository repairTicketRepository,
                         AssetRepository assetRepository,
                         DocumentNumberService documentNumberService) {
        this.repairTicketRepository = repairTicketRepository;
        this.assetRepository = assetRepository;
        this.documentNumberService = documentNumberService;
    }

    @Transactional
    public RepairDto.Response createTicket(RepairDto.CreateRequest req) {
        Asset asset = assetRepository.findById(req.assetId())
                .orElseThrow(() -> new BusinessException("Asset not found", HttpStatus.NOT_FOUND));

        RepairTicket ticket = new RepairTicket();
        ticket.setRepairNo(documentNumberService.nextNumber("REPAIR", "REP"));
        ticket.setAsset(asset);
        ticket.setComplaintDetail(req.complaintDetail());
        ticket.setWarrantyClaim(req.warrantyClaim());
        ticket.setVendorNameSnapshot(req.vendorNameSnapshot());
        ticket.setExpectedReturnDate(req.expectedReturnDate());
        ticket.setCreatedBy(CurrentUserHolder.getUserId());

        // Mark asset as IN_REPAIR
        asset.setAssetStatus("IN_REPAIR");
        assetRepository.save(asset);

        return toResponse(repairTicketRepository.save(ticket));
    }

    @Transactional
    public RepairDto.Response updateTicket(UUID id, RepairDto.UpdateRequest req) {
        RepairTicket ticket = getOrThrow(id);

        if (req.status() != null) ticket.setStatus(req.status());
        if (req.diagnosis() != null) ticket.setDiagnosis(req.diagnosis());
        if (req.repairAction() != null) ticket.setRepairAction(req.repairAction());
        if (req.partsReplaced() != null) ticket.setPartsReplaced(req.partsReplaced());
        if (req.repairCost() != null) ticket.setRepairCost(req.repairCost());
        if (req.sentDate() != null) ticket.setSentDate(req.sentDate());
        if (req.expectedReturnDate() != null) ticket.setExpectedReturnDate(req.expectedReturnDate());
        if (req.receivedDate() != null) ticket.setReceivedDate(req.receivedDate());
        if (req.finalCondition() != null) ticket.setFinalCondition(req.finalCondition());
        if (req.dispatchChallanNo() != null) ticket.setDispatchChallanNo(req.dispatchChallanNo());
        if (req.vendorNameSnapshot() != null) ticket.setVendorNameSnapshot(req.vendorNameSnapshot());
        ticket.setUpdatedBy(CurrentUserHolder.getUserId());

        // On completion, restore asset status
        if ("COMPLETED".equals(req.status()) || "UNREPAIRABLE".equals(req.status())) {
            Asset asset = ticket.getAsset();
            String newCondition = req.finalCondition() != null ? req.finalCondition() : "WORKING";
            asset.setConditionStatus(newCondition);
            asset.setAssetStatus("AVAILABLE");
            assetRepository.save(asset);
        }

        return toResponse(repairTicketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public RepairDto.Response getTicket(UUID id) { return toResponse(getOrThrow(id)); }

    @Transactional(readOnly = true)
    public PageResponse<RepairDto.SummaryResponse> search(String status, UUID assetId, Pageable pageable) {
        Specification<RepairTicket> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (assetId != null) predicates.add(cb.equal(root.get("asset").get("id"), assetId));
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                predicates.add(cb.equal(root.get("status"), status.toUpperCase()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<RepairTicket> page = repairTicketRepository.findAll(spec, pageable);
        List<RepairDto.SummaryResponse> content = page.getContent().stream()
                .map(t -> new RepairDto.SummaryResponse(
                        t.getId(), t.getRepairNo(), t.getAsset().getId(),
                        t.getAsset().getAssetCode(), t.getAsset().getItem().getItemName(),
                        t.getComplaintDate(), t.getStatus(), t.getWarrantyClaim(),
                        t.getRepairCost(), t.getCreatedAt()))
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private RepairTicket getOrThrow(UUID id) {
        return repairTicketRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Repair ticket not found", HttpStatus.NOT_FOUND));
    }

    private RepairDto.Response toResponse(RepairTicket t) {
        return new RepairDto.Response(
                t.getId(), t.getRepairNo(), t.getAsset().getId(),
                t.getAsset().getAssetCode(), t.getAsset().getItem().getItemName(),
                t.getComplaintDate(), t.getComplaintDetail(), t.getWarrantyClaim(),
                t.getVendorNameSnapshot(), t.getDispatchChallanNo(),
                t.getSentDate(), t.getExpectedReturnDate(), t.getReceivedDate(),
                t.getDiagnosis(), t.getRepairAction(), t.getPartsReplaced(),
                t.getRepairCost(), t.getStatus(), t.getFinalCondition(),
                t.getCreatedAt(), t.getCreatedBy(), t.getUpdatedAt(), t.getVersion());
    }
}
