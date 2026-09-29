package com.nicsi.store.asset.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.domain.Disposal;
import com.nicsi.store.asset.domain.DisposalItem;
import com.nicsi.store.asset.domain.RepairTicket;
import com.nicsi.store.asset.dto.AssetDto;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.asset.repository.DisposalRepository;
import com.nicsi.store.asset.repository.RepairTicketRepository;
import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.grn.domain.Grn;
import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.issue.domain.AssetAssignment;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
import com.nicsi.store.master.domain.Item;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssetRegistrationService {

    private final AssetRepository assetRepository;
    private final AssetCodeGenerator assetCodeGenerator;
    private final AssetAssignmentRepository assetAssignmentRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final RepairTicketRepository repairTicketRepository;
    private final DisposalRepository disposalRepository;
    private final AuditService auditService;

    public AssetRegistrationService(
            AssetRepository assetRepository,
            AssetCodeGenerator assetCodeGenerator,
            AssetAssignmentRepository assetAssignmentRepository,
            StorageLocationRepository storageLocationRepository,
            StoreSiteRepository storeSiteRepository,
            RepairTicketRepository repairTicketRepository,
            DisposalRepository disposalRepository,
            AuditService auditService
    ) {
        this.assetRepository = assetRepository;
        this.assetCodeGenerator = assetCodeGenerator;
        this.assetAssignmentRepository = assetAssignmentRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.repairTicketRepository = repairTicketRepository;
        this.disposalRepository = disposalRepository;
        this.auditService = auditService;
    }

    /**
     * Registers individual asset records for accepted serialised GRN items.
     * Executes in active transaction (Propagation.REQUIRED).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Asset> registerAssetsFromGrn(GrnItem grnItem, List<String> serialNumbers) {
        Item item = grnItem.getItem();
        Grn grn = grnItem.getGrn();
        int count = grnItem.getAcceptedQty().intValue();
        if (count <= 0) {
            return List.of();
        }

        String categoryCode = item.getCategory() != null ? item.getCategory().getCategoryCode() : "GEN";
        LocalDate purchaseDate = grn.getGrnDate() != null ? grn.getGrnDate() : LocalDate.now();
        UUID currentUserId = CurrentUserHolder.getUserId();

        List<Asset> createdAssets = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String serial = (serialNumbers != null && i < serialNumbers.size()) ? serialNumbers.get(i) : null;
            if (serial != null && !serial.isBlank()) {
                serial = serial.trim();
            } else {
                serial = null;
            }

            String assetCode = assetCodeGenerator.generateAssetCode(categoryCode, purchaseDate);
            String qrCode = "NICSI-AST-" + UUID.randomUUID();

            Asset asset = new Asset();
            asset.setAssetCode(assetCode);
            asset.setItem(item);
            asset.setGrnItem(grnItem);
            asset.setSerialNumber(serial);
            asset.setManufacturer(item.getManufacturerDefault());
            asset.setModelNumber(item.getModelDefault());
            asset.setPurchaseDate(purchaseDate);
            asset.setPurchaseCost(grnItem.getUnitRate() != null ? grnItem.getUnitRate() : BigDecimal.ZERO);
            if (grn.getPurchaseOrderRef() != null) {
                asset.setPoNumberSnapshot(grn.getPurchaseOrderRef().getPoNumber());
            }
            asset.setInvoiceNumberSnapshot(grn.getInvoiceNumber());
            asset.setStore(grn.getStore());
            asset.setLocation(grnItem.getReceivingLocation());
            asset.setAssetStatus("AVAILABLE");
            asset.setConditionStatus("GOOD");
            asset.setQrCodeValue(qrCode);
            asset.setBarcodeValue(assetCode);
            asset.setCreatedBy(currentUserId);
            asset.setUpdatedBy(currentUserId);

            createdAssets.add(assetRepository.save(asset));
        }

        return createdAssets;
    }

    @Transactional(readOnly = true)
    public AssetDto.Response getAsset(UUID id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with id: " + id, HttpStatus.NOT_FOUND));
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public AssetDto.Response getAssetByCode(String assetCode) {
        Asset asset = assetRepository.findByAssetCode(assetCode)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with code: " + assetCode, HttpStatus.NOT_FOUND));
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetDto.SummaryResponse> search(
            UUID storeId, UUID itemId, String assetStatus, String conditionStatus, String search, Pageable pageable
    ) {
        Page<Asset> page = assetRepository.search(storeId, itemId, assetStatus, conditionStatus, search, pageable);
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    @Transactional
    public AssetDto.Response assignAsset(UUID assetId, AssetDto.AssignRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with id: " + assetId, HttpStatus.NOT_FOUND));

        if (!Set.of("AVAILABLE", "ISSUED", "RESERVED").contains(asset.getAssetStatus())) {
            throw new BusinessException("INVALID_ASSET_STATUS",
                    "Asset in status " + asset.getAssetStatus() + " cannot be assigned", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        // Close any existing active assignment
        assetAssignmentRepository.findFirstByAssetIdAndStatus(assetId, "ACTIVE").ifPresent(prev -> {
            prev.setStatus("TRANSFERRED");
            prev.setAssignedUntil(Instant.now());
            assetAssignmentRepository.save(prev);
        });

        // Create new AssetAssignment
        AssetAssignment assignment = new AssetAssignment();
        assignment.setAssetId(asset.getId());
        assignment.setAssignmentType(request.assignmentType() != null ? request.assignmentType() : "EMPLOYEE");
        assignment.setAssigneeUserId(request.assigneeUserId());
        assignment.setAssigneeNameSnapshot(request.assigneeNameSnapshot());
        assignment.setDepartmentId(request.departmentId());
        assignment.setProjectId(request.projectId());
        assignment.setLocationId(request.locationId());
        assignment.setAssignedFrom(Instant.now());
        assignment.setStatus("ACTIVE");
        assignment.setCreatedBy(currentUserId);
        assetAssignmentRepository.save(assignment);

        // Update Asset
        asset.setAssetStatus("ISSUED");
        asset.setCurrentCustodianUserId(request.assigneeUserId());
        asset.setCurrentDepartmentId(request.departmentId());
        asset.setCurrentProjectId(request.projectId());
        if (request.locationId() != null) {
            storageLocationRepository.findById(request.locationId()).ifPresent(asset::setLocation);
        }
        asset.setUpdatedBy(currentUserId);
        asset.setUpdatedAt(Instant.now());
        Asset saved = assetRepository.save(asset);

        auditService.record(new AuditEvent(
                "ASSET", "ASSIGN", "ASSET", assetId, asset.getAssetCode(),
                null, "ISSUED", request.remarks()
        ));

        return toResponse(saved);
    }

    @Transactional
    public AssetDto.Response transferAsset(UUID assetId, AssetDto.TransferRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with id: " + assetId, HttpStatus.NOT_FOUND));

        if (Set.of("DISPOSED", "CONDEMNED", "LOST", "IN_REPAIR").contains(asset.getAssetStatus())) {
            throw new BusinessException("INVALID_ASSET_STATUS",
                    "Asset in status " + asset.getAssetStatus() + " cannot be transferred", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        // Close existing active assignment
        assetAssignmentRepository.findFirstByAssetIdAndStatus(assetId, "ACTIVE").ifPresent(prev -> {
            prev.setStatus("TRANSFERRED");
            prev.setAssignedUntil(Instant.now());
            assetAssignmentRepository.save(prev);
        });

        // Update store and location if provided
        if (request.toStoreId() != null) {
            storeSiteRepository.findById(request.toStoreId()).ifPresent(asset::setStore);
        }
        if (request.toLocationId() != null) {
            storageLocationRepository.findById(request.toLocationId()).ifPresent(asset::setLocation);
        }

        if (request.toCustodianUserId() != null || request.toDepartmentId() != null || request.toProjectId() != null) {
            // New assignment
            AssetAssignment newAssignment = new AssetAssignment();
            newAssignment.setAssetId(asset.getId());
            newAssignment.setAssignmentType(request.assignmentType() != null ? request.assignmentType() : "EMPLOYEE");
            newAssignment.setAssigneeUserId(request.toCustodianUserId());
            newAssignment.setAssigneeNameSnapshot(request.toCustodianNameSnapshot());
            newAssignment.setDepartmentId(request.toDepartmentId());
            newAssignment.setProjectId(request.toProjectId());
            newAssignment.setLocationId(asset.getLocation().getId());
            newAssignment.setAssignedFrom(Instant.now());
            newAssignment.setStatus("ACTIVE");
            newAssignment.setCreatedBy(currentUserId);
            assetAssignmentRepository.save(newAssignment);

            asset.setCurrentCustodianUserId(request.toCustodianUserId());
            asset.setCurrentDepartmentId(request.toDepartmentId());
            asset.setCurrentProjectId(request.toProjectId());
            asset.setAssetStatus("ISSUED");
        } else {
            // Transferred to store without custodian
            asset.setCurrentCustodianUserId(null);
            asset.setCurrentDepartmentId(null);
            asset.setCurrentProjectId(null);
            asset.setAssetStatus("AVAILABLE");
        }

        asset.setUpdatedBy(currentUserId);
        asset.setUpdatedAt(Instant.now());
        Asset saved = assetRepository.save(asset);

        auditService.record(new AuditEvent(
                "ASSET", "TRANSFER", "ASSET", assetId, asset.getAssetCode(),
                null, asset.getAssetStatus(), request.remarks()
        ));

        return toResponse(saved);
    }

    @Transactional
    public AssetDto.Response returnAsset(UUID assetId, AssetDto.ReturnRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with id: " + assetId, HttpStatus.NOT_FOUND));

        if (Set.of("DISPOSED", "CONDEMNED", "AVAILABLE").contains(asset.getAssetStatus())) {
            throw new BusinessException("INVALID_ASSET_STATUS",
                    "Asset is already in status " + asset.getAssetStatus() + " and cannot be returned", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        // Close existing active assignment
        assetAssignmentRepository.findFirstByAssetIdAndStatus(assetId, "ACTIVE").ifPresent(prev -> {
            prev.setStatus("RETURNED");
            prev.setAssignedUntil(Instant.now());
            assetAssignmentRepository.save(prev);
        });

        // Clear current custodians
        asset.setCurrentCustodianUserId(null);
        asset.setCurrentDepartmentId(null);
        asset.setCurrentProjectId(null);

        // Update return store/location
        if (request.returnStoreId() != null) {
            storeSiteRepository.findById(request.returnStoreId()).ifPresent(asset::setStore);
        }
        if (request.returnLocationId() != null) {
            storageLocationRepository.findById(request.returnLocationId()).ifPresent(asset::setLocation);
        }

        // Apply disposition
        String disposition = request.disposition() != null ? request.disposition().toUpperCase() : "RESTOCK";
        if ("REPAIR".equals(disposition)) {
            asset.setAssetStatus("IN_REPAIR");
            asset.setConditionStatus("REPAIR_REQUIRED");
        } else if ("CONDEMNATION".equals(disposition)) {
            asset.setAssetStatus("CONDEMNED");
            asset.setConditionStatus("UNSERVICEABLE");
        } else if ("SCRAP".equals(disposition)) {
            asset.setAssetStatus("CONDEMNED");
            asset.setConditionStatus("SCRAP");
        } else {
            asset.setAssetStatus("AVAILABLE");
            if (request.conditionStatus() != null && !request.conditionStatus().isBlank()) {
                asset.setConditionStatus(request.conditionStatus());
            } else {
                asset.setConditionStatus("GOOD");
            }
        }

        asset.setUpdatedBy(currentUserId);
        asset.setUpdatedAt(Instant.now());
        Asset saved = assetRepository.save(asset);

        auditService.record(new AuditEvent(
                "ASSET", "RETURN", "ASSET", assetId, asset.getAssetCode(),
                null, asset.getAssetStatus(), request.remarks()
        ));

        return toResponse(saved);
    }

    @Transactional
    public AssetDto.Response repairAsset(UUID assetId, AssetDto.RepairRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with id: " + assetId, HttpStatus.NOT_FOUND));

        if ("DISPOSED".equals(asset.getAssetStatus())) {
            throw new BusinessException("INVALID_ASSET_STATUS",
                    "Asset is DISPOSED and cannot be repaired", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        String action = request.action() != null ? request.action().toUpperCase() : "SEND_TO_REPAIR";

        if ("RETURN_FROM_REPAIR".equals(action)) {
            asset.setAssetStatus("AVAILABLE");
            if (request.finalCondition() != null && !request.finalCondition().isBlank()) {
                asset.setConditionStatus(request.finalCondition());
            } else {
                asset.setConditionStatus("GOOD");
            }

            // Complete open repair ticket if present
            repairTicketRepository.findFirstByAssetIdAndStatusIn(assetId, List.of("OPEN", "APPROVED", "SENT_TO_VENDOR", "UNDER_REPAIR"))
                    .ifPresent(ticket -> {
                        ticket.setStatus("COMPLETED");
                        ticket.setReceivedDate(LocalDate.now());
                        if (request.repairCost() != null) ticket.setRepairCost(request.repairCost());
                        if (request.partsReplaced() != null) ticket.setPartsReplaced(request.partsReplaced());
                        if (request.diagnosis() != null) ticket.setDiagnosis(request.diagnosis());
                        if (request.finalCondition() != null) ticket.setFinalCondition(request.finalCondition());
                        ticket.setUpdatedBy(currentUserId);
                        ticket.setUpdatedAt(Instant.now());
                        repairTicketRepository.save(ticket);
                    });

            auditService.record(new AuditEvent(
                    "ASSET", "REPAIR_COMPLETED", "ASSET", assetId, asset.getAssetCode(),
                    "IN_REPAIR", "AVAILABLE", request.remarks()
            ));
        } else {
            // SEND_TO_REPAIR
            // Close active assignment if issued
            assetAssignmentRepository.findFirstByAssetIdAndStatus(assetId, "ACTIVE").ifPresent(prev -> {
                prev.setStatus("RETURNED");
                prev.setAssignedUntil(Instant.now());
                assetAssignmentRepository.save(prev);
            });
            asset.setCurrentCustodianUserId(null);

            asset.setAssetStatus("IN_REPAIR");
            asset.setConditionStatus("REPAIR_REQUIRED");

            RepairTicket ticket = new RepairTicket();
            ticket.setRepairNo("RPR-" + System.currentTimeMillis());
            ticket.setAsset(asset);
            ticket.setComplaintDate(LocalDate.now());
            ticket.setComplaintDetail(request.complaintDetail() != null ? request.complaintDetail() : "Maintenance request");
            ticket.setWarrantyClaim(Boolean.TRUE.equals(request.warrantyClaim()));
            ticket.setVendorId(request.vendorId());
            ticket.setVendorNameSnapshot(request.vendorNameSnapshot());
            ticket.setSentDate(LocalDate.now());
            ticket.setStatus("SENT_TO_VENDOR");
            ticket.setCreatedBy(currentUserId);
            repairTicketRepository.save(ticket);

            auditService.record(new AuditEvent(
                    "ASSET", "REPAIR_SENT", "ASSET", assetId, asset.getAssetCode(),
                    null, "IN_REPAIR", request.remarks()
            ));
        }

        asset.setUpdatedBy(currentUserId);
        asset.setUpdatedAt(Instant.now());
        Asset saved = assetRepository.save(asset);

        return toResponse(saved);
    }

    @Transactional
    public AssetDto.Response disposeAsset(UUID assetId, AssetDto.DisposeRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException("ASSET_NOT_FOUND", "Asset not found with id: " + assetId, HttpStatus.NOT_FOUND));

        if ("DISPOSED".equals(asset.getAssetStatus())) {
            throw new BusinessException("INVALID_ASSET_STATUS",
                    "Asset is already DISPOSED", HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        // Close any active assignment
        assetAssignmentRepository.findFirstByAssetIdAndStatus(assetId, "ACTIVE").ifPresent(prev -> {
            prev.setStatus("CANCELLED");
            prev.setAssignedUntil(Instant.now());
            assetAssignmentRepository.save(prev);
        });

        asset.setCurrentCustodianUserId(null);
        asset.setCurrentDepartmentId(null);
        asset.setCurrentProjectId(null);
        asset.setAssetStatus("DISPOSED");
        asset.setConditionStatus("SCRAP");
        asset.setUpdatedBy(currentUserId);
        asset.setUpdatedAt(Instant.now());
        Asset saved = assetRepository.save(asset);

        // Record disposal entry
        Disposal disposal = new Disposal();
        disposal.setDisposalNo("DSP-" + System.currentTimeMillis());
        disposal.setDisposalDate(LocalDate.now());
        disposal.setDisposalMethod(request.disposalMethod() != null ? request.disposalMethod() : "SCRAP");
        disposal.setPurchaserVendorId(request.purchaserVendorId());
        disposal.setPurchaserNameSnapshot(request.purchaserNameSnapshot());
        disposal.setSaleAmount(request.saleAmount() != null ? request.saleAmount() : BigDecimal.ZERO);
        disposal.setCertificateNumber(request.certificateNumber());
        disposal.setStatus("COMPLETED");
        disposal.setCreatedBy(currentUserId);
        disposal.setApprovedBy(currentUserId);
        disposal.setApprovedAt(Instant.now());
        disposal.setPostedBy(currentUserId);
        disposal.setPostedAt(Instant.now());

        DisposalItem item = new DisposalItem();
        item.setDisposal(disposal);
        item.setAsset(asset);
        item.setRealizedValue(disposal.getSaleAmount());
        item.setRemarks(request.remarks());
        disposal.getItems().add(item);

        disposalRepository.save(disposal);

        auditService.record(new AuditEvent(
                "ASSET", "DISPOSE", "ASSET", assetId, asset.getAssetCode(),
                null, "DISPOSED", request.remarks()
        ));

        return toResponse(saved);
    }

    public AssetDto.Response toResponse(Asset a) {
        return new AssetDto.Response(
                a.getId(),
                a.getAssetCode(),
                a.getItem().getId(),
                a.getItem().getItemCode(),
                a.getItem().getItemName(),
                a.getGrnItem() != null ? a.getGrnItem().getId() : null,
                a.getSerialNumber(),
                a.getManufacturer(),
                a.getModelNumber(),
                a.getConfiguration(),
                a.getPurchaseDate(),
                a.getPurchaseCost(),
                a.getPoNumberSnapshot(),
                a.getInvoiceNumberSnapshot(),
                a.getStore().getId(),
                a.getStore().getStoreCode(),
                a.getStore().getStoreName(),
                a.getLocation().getId(),
                a.getLocation().getLocationCode(),
                a.getLocation().getLocationName(),
                a.getCurrentCustodianUserId(),
                a.getCurrentDepartmentId(),
                a.getCurrentProjectId(),
                a.getAssetStatus(),
                a.getConditionStatus(),
                a.getQrCodeValue(),
                a.getBarcodeValue(),
                a.getWarrantyStartDate(),
                a.getWarrantyEndDate(),
                a.getCapitalizationRef(),
                a.getRemarks(),
                a.getCreatedAt(),
                a.getVersion()
        );
    }

    public AssetDto.SummaryResponse toSummaryResponse(Asset a) {
        return new AssetDto.SummaryResponse(
                a.getId(),
                a.getAssetCode(),
                a.getItem().getItemCode(),
                a.getItem().getItemName(),
                a.getSerialNumber(),
                a.getStore().getStoreCode(),
                a.getLocation().getLocationCode(),
                a.getAssetStatus(),
                a.getConditionStatus(),
                a.getQrCodeValue(),
                a.getPurchaseDate(),
                a.getPurchaseCost()
        );
    }
}
