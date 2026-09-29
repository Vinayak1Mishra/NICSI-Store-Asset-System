package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.dto.StorageLocationDto;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.validation.HierarchyCycleValidator;
import com.nicsi.store.master.validation.MasterInUseValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StorageLocationService {

    private final StorageLocationRepository repo;
    private final StoreSiteRepository storeRepo;
    private final AuditService auditService;
    private final MasterInUseValidator inUseValidator;
    private final HierarchyCycleValidator hierarchyCycleValidator;

    public StorageLocationService(
            StorageLocationRepository repo,
            StoreSiteRepository storeRepo,
            AuditService auditService,
            MasterInUseValidator inUseValidator,
            HierarchyCycleValidator hierarchyCycleValidator) {
        this.repo = repo;
        this.storeRepo = storeRepo;
        this.auditService = auditService;
        this.inUseValidator = inUseValidator;
        this.hierarchyCycleValidator = hierarchyCycleValidator;
    }

    @Transactional
    public StorageLocationDto.Response create(StorageLocationDto.CreateRequest req) {
        StoreSite store = storeRepo.findById(req.storeId())
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND", "Store not found", HttpStatus.NOT_FOUND));

        if (repo.existsByStoreIdAndLocationCodeIgnoreCase(req.storeId(), req.locationCode())) {
            throw new BusinessException("DUPLICATE_CODE", "Location code already exists in this store", HttpStatus.CONFLICT);
        }

        hierarchyCycleValidator.validate(null, req.parentLocationId(), req.storeId(), repo);

        StorageLocation parent = null;
        if (req.parentLocationId() != null) {
            parent = repo.findById(req.parentLocationId()).orElse(null);
        }

        StorageLocation loc = new StorageLocation();
        loc.setStore(store);
        loc.setParentLocation(parent);
        loc.setLocationCode(req.locationCode());
        loc.setLocationName(req.locationName());
        loc.setLocationType(req.locationType());
        loc.setBarcodeValue(req.barcodeValue());
        loc.setActive(true);
        loc.setCreatedBy(CurrentUserHolder.getUserId());
        loc.setUpdatedBy(CurrentUserHolder.getUserId());

        loc = repo.save(loc);
        StorageLocationDto.Response response = toResponse(loc);

        auditService.record(new AuditEvent("STORE", "CREATE", "STORAGE_LOCATION", loc.getId(), loc.getLocationCode(), null, response, null));
        return response;
    }

    @Transactional
    public StorageLocationDto.Response update(UUID id, StorageLocationDto.UpdateRequest req) {
        StorageLocation loc = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Location not found", HttpStatus.NOT_FOUND));
        if (req.version() != null && !req.version().equals(loc.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Location was updated by another transaction", HttpStatus.CONFLICT);
        }
        StorageLocationDto.Response oldValue = toResponse(loc);

        hierarchyCycleValidator.validate(id, req.parentLocationId(), loc.getStore().getId(), repo);

        StorageLocation parent = null;
        if (req.parentLocationId() != null) {
            parent = repo.findById(req.parentLocationId()).orElse(null);
        }

        loc.setParentLocation(parent);
        loc.setLocationName(req.locationName());
        loc.setLocationType(req.locationType());
        loc.setBarcodeValue(req.barcodeValue());
        loc.setUpdatedBy(CurrentUserHolder.getUserId());
        loc = repo.saveAndFlush(loc);
        StorageLocationDto.Response newValue = toResponse(loc);

        auditService.record(new AuditEvent("STORE", "UPDATE", "STORAGE_LOCATION", loc.getId(), loc.getLocationCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID id, boolean active) {
        StorageLocation loc = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Location not found", HttpStatus.NOT_FOUND));
        StorageLocationDto.Response oldValue = toResponse(loc);

        if (!active) {
            inUseValidator.validateLocationDeactivation(id);
        }

        loc.setActive(active);
        loc.setUpdatedBy(CurrentUserHolder.getUserId());
        loc = repo.save(loc);
        StorageLocationDto.Response newValue = toResponse(loc);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "STORAGE_LOCATION", loc.getId(), loc.getLocationCode(), oldValue, newValue, null));
    }

    public PageResponse<StorageLocationDto.Response> list(UUID storeId, UUID parentLocationId, String locationType, Boolean active, Pageable pageable) {
        Page<StorageLocation> page = repo.search(storeId, parentLocationId, locationType, active, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public StorageLocationDto.Response getById(UUID id) {
        StorageLocation loc = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Location not found", HttpStatus.NOT_FOUND));
        return toResponse(loc);
    }
    
    public List<StorageLocationDto.TreeResponse> getTree(UUID storeId) {
        List<StorageLocation> all = repo.findByStoreId(storeId);
        return buildTree(all, null);
    }

    private List<StorageLocationDto.TreeResponse> buildTree(List<StorageLocation> all, UUID parentId) {
        return all.stream()
            .filter(loc -> Objects.equals(parentId, loc.getParentLocation() == null ? null : loc.getParentLocation().getId()))
            .map(loc -> new StorageLocationDto.TreeResponse(
                    loc.getId(), loc.getLocationCode(), loc.getLocationName(), 
                    loc.getLocationType(), loc.getBarcodeValue(), loc.isActive(), 
                    buildTree(all, loc.getId())))
            .toList();
    }

    private StorageLocationDto.Response toResponse(StorageLocation loc) {
        return new StorageLocationDto.Response(
                loc.getId(),
                loc.getStore().getId(),
                loc.getStore().getStoreCode(),
                loc.getStore().getStoreName(),
                loc.getParentLocation() != null ? loc.getParentLocation().getId() : null,
                loc.getParentLocation() != null ? loc.getParentLocation().getLocationCode() : null,
                loc.getParentLocation() != null ? loc.getParentLocation().getLocationName() : null,
                loc.getLocationCode(),
                loc.getLocationName(),
                loc.getLocationType(),
                loc.getBarcodeValue(),
                loc.isActive(),
                loc.getCreatedAt(),
                loc.getCreatedBy(),
                loc.getUpdatedAt(),
                loc.getUpdatedBy(),
                loc.getVersion() != null ? loc.getVersion() : 0L
        );
    }
}
