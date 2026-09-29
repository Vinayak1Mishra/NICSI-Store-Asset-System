package com.nicsi.store.license.service;

import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.grn.repository.GrnItemRepository;
import com.nicsi.store.license.domain.SoftwareLicense;
import com.nicsi.store.license.domain.SoftwareLicenseAllocation;
import com.nicsi.store.license.dto.SoftwareLicenseDto;
import com.nicsi.store.license.repository.SoftwareLicenseAllocationRepository;
import com.nicsi.store.license.repository.SoftwareLicenseRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SoftwareLicenseService {

    private final SoftwareLicenseRepository licenseRepository;
    private final SoftwareLicenseAllocationRepository allocationRepository;
    private final ItemRepository itemRepository;
    private final AssetRepository assetRepository;
    private final GrnItemRepository grnItemRepository;
    private final AuditService auditService;

    public SoftwareLicenseService(
            SoftwareLicenseRepository licenseRepository,
            SoftwareLicenseAllocationRepository allocationRepository,
            ItemRepository itemRepository,
            AssetRepository assetRepository,
            GrnItemRepository grnItemRepository,
            AuditService auditService
    ) {
        this.licenseRepository = licenseRepository;
        this.allocationRepository = allocationRepository;
        this.itemRepository = itemRepository;
        this.assetRepository = assetRepository;
        this.grnItemRepository = grnItemRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SoftwareLicenseDto.Response createLicense(SoftwareLicenseDto.CreateRequest request) {
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found: " + request.itemId(), HttpStatus.NOT_FOUND));

        if (licenseRepository.findByLicenseCodeIgnoreCase(request.licenseCode().trim()).isPresent()) {
            throw new BusinessException("DUPLICATE_LICENSE_CODE", "License code already exists: " + request.licenseCode(), HttpStatus.CONFLICT);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        SoftwareLicense license = new SoftwareLicense();
        license.setItem(item);
        license.setLicenseCode(request.licenseCode().trim());
        license.setVendorId(request.vendorId());
        license.setLicenseType(request.licenseType().trim().toUpperCase());
        license.setEntitlementQty(request.entitlementQty());
        license.setAllocatedQty(BigDecimal.ZERO);
        license.setLicenseKeySecretRef(request.licenseKeySecretRef());
        license.setPurchaseDate(request.purchaseDate());
        license.setStartDate(request.startDate());
        license.setEndDate(request.endDate());
        license.setPoNumberSnapshot(request.poNumberSnapshot());
        license.setStatus("ACTIVE");
        license.setCreatedBy(currentUserId);
        license.setUpdatedBy(currentUserId);

        SoftwareLicense saved = licenseRepository.save(license);

        auditService.record(new AuditEvent(
                "LICENSE", "CREATE", "SOFTWARE_LICENSE", saved.getId(), saved.getLicenseCode(),
                null, "ACTIVE", "Created software license entitlement"
        ));

        return toResponse(saved);
    }

    @Transactional
    public SoftwareLicenseDto.Response intakeFromGrn(UUID grnItemId, SoftwareLicenseDto.CreateRequest request) {
        GrnItem grnItem = grnItemRepository.findById(grnItemId)
                .orElseThrow(() -> new BusinessException("GRN_ITEM_NOT_FOUND", "GRN item not found: " + grnItemId, HttpStatus.NOT_FOUND));

        SoftwareLicenseDto.Response created = createLicense(request);

        // Update GRN item remarks with durable reference to the intake
        String prevRemarks = grnItem.getRemarks() != null ? grnItem.getRemarks() : "";
        grnItem.setRemarks(prevRemarks + " [INTAKE_COMPLETED:" + created.licenseCode() + "]");
        grnItemRepository.save(grnItem);

        auditService.record(new AuditEvent(
                "GRN", "INTAKE_LICENSE", "GRN_ITEM", grnItemId, created.licenseCode(),
                "PENDING", "INTAKEN", "Software license intaken from GRN line"
        ));

        return created;
    }

    @Transactional
    public SoftwareLicenseDto.Response allocateLicense(UUID licenseId, SoftwareLicenseDto.AllocateRequest request) {
        SoftwareLicense license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new BusinessException("LICENSE_NOT_FOUND", "License not found: " + licenseId, HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(license.getStatus())) {
            throw new BusinessException("INVALID_LICENSE_STATUS", "Cannot allocate from non-active license: " + license.getStatus(), HttpStatus.BAD_REQUEST);
        }

        BigDecimal available = license.getEntitlementQty().subtract(license.getAllocatedQty());
        if (request.quantity().compareTo(available) > 0) {
            throw new BusinessException("INSUFFICIENT_ENTITLEMENT",
                    "Requested quantity (" + request.quantity() + ") exceeds available (" + available + ")",
                    HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();

        Asset asset = null;
        if (request.assetId() != null) {
            asset = assetRepository.findById(request.assetId()).orElse(null);
        }

        SoftwareLicenseAllocation allocation = new SoftwareLicenseAllocation();
        allocation.setSoftwareLicense(license);
        allocation.setAllocationType(request.allocationType().trim().toUpperCase());
        allocation.setUserId(request.userId());
        allocation.setAsset(asset);
        allocation.setServerIdentifier(request.serverIdentifier());
        allocation.setQuantity(request.quantity());
        allocation.setAllocatedAt(Instant.now());
        allocation.setAllocatedBy(currentUserId);
        allocation.setStatus("ACTIVE");

        allocationRepository.save(allocation);

        // Update allocated quantity
        license.setAllocatedQty(license.getAllocatedQty().add(request.quantity()));
        license.setUpdatedBy(currentUserId);
        license.setUpdatedAt(Instant.now());
        SoftwareLicense updated = licenseRepository.save(license);

        auditService.record(new AuditEvent(
                "LICENSE", "ALLOCATE", "SOFTWARE_LICENSE_ALLOCATION", allocation.getId(), license.getLicenseCode(),
                null, "ACTIVE", request.remarks()
        ));

        return toResponse(updated);
    }

    @Transactional
    public SoftwareLicenseDto.Response releaseAllocation(UUID licenseId, UUID allocationId, String remarks) {
        SoftwareLicense license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new BusinessException("LICENSE_NOT_FOUND", "License not found: " + licenseId, HttpStatus.NOT_FOUND));

        SoftwareLicenseAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new BusinessException("ALLOCATION_NOT_FOUND", "Allocation not found: " + allocationId, HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equalsIgnoreCase(allocation.getStatus())) {
            throw new BusinessException("INVALID_ALLOCATION_STATUS", "Allocation is already " + allocation.getStatus(), HttpStatus.BAD_REQUEST);
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        allocation.setStatus("RELEASED");
        allocation.setReleasedAt(Instant.now());
        allocation.setReleasedBy(currentUserId);
        allocationRepository.save(allocation);

        // Deduct from allocatedQty
        BigDecimal newAllocated = license.getAllocatedQty().subtract(allocation.getQuantity());
        if (newAllocated.compareTo(BigDecimal.ZERO) < 0) {
            newAllocated = BigDecimal.ZERO;
        }
        license.setAllocatedQty(newAllocated);
        license.setUpdatedBy(currentUserId);
        license.setUpdatedAt(Instant.now());
        SoftwareLicense updated = licenseRepository.save(license);

        auditService.record(new AuditEvent(
                "LICENSE", "RELEASE", "SOFTWARE_LICENSE_ALLOCATION", allocationId, license.getLicenseCode(),
                "ACTIVE", "RELEASED", remarks
        ));

        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public SoftwareLicenseDto.Response getLicense(UUID id) {
        SoftwareLicense license = licenseRepository.findById(id)
                .orElseThrow(() -> new BusinessException("LICENSE_NOT_FOUND", "License not found: " + id, HttpStatus.NOT_FOUND));
        return toResponse(license);
    }

    @Transactional(readOnly = true)
    public PageResponse<SoftwareLicenseDto.SummaryResponse> search(
            String status, String licenseType, String search, Pageable pageable
    ) {
        Page<SoftwareLicense> page = licenseRepository.search(
                status != null && !status.isBlank() ? status : null,
                licenseType != null && !licenseType.isBlank() ? licenseType : null,
                search != null && !search.isBlank() ? search : null,
                pageable
        );
        return PageResponse.from(page.map(this::toSummaryResponse));
    }

    public SoftwareLicenseDto.Response toResponse(SoftwareLicense sl) {
        List<SoftwareLicenseAllocation> allocs = allocationRepository.findBySoftwareLicenseId(sl.getId());
        List<SoftwareLicenseDto.AllocationResponse> allocResponses = allocs.stream().map(a ->
                new SoftwareLicenseDto.AllocationResponse(
                        a.getId(),
                        sl.getId(),
                        a.getAllocationType(),
                        a.getUserId(),
                        a.getAsset() != null ? a.getAsset().getId() : null,
                        a.getAsset() != null ? a.getAsset().getAssetCode() : null,
                        a.getServerIdentifier(),
                        a.getQuantity(),
                        a.getAllocatedAt(),
                        a.getAllocatedBy(),
                        a.getReleasedAt(),
                        a.getStatus()
                )
        ).toList();

        BigDecimal available = sl.getEntitlementQty().subtract(sl.getAllocatedQty());

        return new SoftwareLicenseDto.Response(
                sl.getId(),
                sl.getItem().getId(),
                sl.getItem().getItemCode(),
                sl.getItem().getItemName(),
                sl.getLicenseCode(),
                sl.getVendorId(),
                sl.getLicenseType(),
                sl.getEntitlementQty(),
                sl.getAllocatedQty(),
                available,
                sl.getLicenseKeySecretRef(),
                sl.getPurchaseDate(),
                sl.getStartDate(),
                sl.getEndDate(),
                sl.getPoNumberSnapshot(),
                sl.getStatus(),
                sl.getCreatedAt(),
                sl.getVersion(),
                allocResponses
        );
    }

    public SoftwareLicenseDto.SummaryResponse toSummaryResponse(SoftwareLicense sl) {
        BigDecimal available = sl.getEntitlementQty().subtract(sl.getAllocatedQty());
        return new SoftwareLicenseDto.SummaryResponse(
                sl.getId(),
                sl.getItem().getId(),
                sl.getItem().getItemCode(),
                sl.getItem().getItemName(),
                sl.getLicenseCode(),
                sl.getLicenseType(),
                sl.getEntitlementQty(),
                sl.getAllocatedQty(),
                available,
                sl.getStartDate(),
                sl.getEndDate(),
                sl.getStatus()
        );
    }
}
