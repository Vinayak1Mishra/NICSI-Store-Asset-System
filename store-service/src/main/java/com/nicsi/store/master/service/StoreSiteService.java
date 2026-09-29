package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.dto.StoreSiteDto;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.validation.MasterInUseValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StoreSiteService {

    private final StoreSiteRepository repo;
    private final AuditService auditService;
    private final MasterInUseValidator inUseValidator;

    public StoreSiteService(StoreSiteRepository repo, AuditService auditService, MasterInUseValidator inUseValidator) {
        this.repo = repo;
        this.auditService = auditService;
        this.inUseValidator = inUseValidator;
    }

    @Transactional
    public StoreSiteDto.Response create(StoreSiteDto.CreateRequest req) {
        if (repo.existsByStoreCodeIgnoreCase(req.storeCode())) {
            throw new BusinessException("DUPLICATE_CODE", "Store code already exists", HttpStatus.CONFLICT);
        }

        StoreSite store = new StoreSite();
        store.setStoreCode(req.storeCode());
        store.setStoreName(req.storeName());
        store.setOfficeLocationId(req.officeLocationId());
        store.setOfficeCodeSnapshot(req.officeCodeSnapshot());
        store.setOfficeNameSnapshot(req.officeNameSnapshot());
        store.setAddress(req.address());
        store.setStoreType(req.storeType());
        store.setActive(true);
        store.setCreatedBy(CurrentUserHolder.getUserId());
        store.setUpdatedBy(CurrentUserHolder.getUserId());

        store = repo.save(store);
        StoreSiteDto.Response response = toResponse(store);

        auditService.record(new AuditEvent("STORE", "CREATE", "STORE_SITE", store.getId(), store.getStoreCode(), null, response, null));
        return response;
    }

    @Transactional
    public StoreSiteDto.Response update(UUID id, StoreSiteDto.UpdateRequest req) {
        StoreSite store = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Store not found", HttpStatus.NOT_FOUND));
        if (req.version() != null && !req.version().equals(store.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Store was updated by another transaction", HttpStatus.CONFLICT);
        }
        StoreSiteDto.Response oldValue = toResponse(store);

        store.setStoreName(req.storeName());
        store.setOfficeLocationId(req.officeLocationId());
        store.setOfficeCodeSnapshot(req.officeCodeSnapshot());
        store.setOfficeNameSnapshot(req.officeNameSnapshot());
        store.setAddress(req.address());
        store.setStoreType(req.storeType());
        store.setUpdatedBy(CurrentUserHolder.getUserId());

        store = repo.saveAndFlush(store);
        StoreSiteDto.Response newValue = toResponse(store);

        auditService.record(new AuditEvent("STORE", "UPDATE", "STORE_SITE", store.getId(), store.getStoreCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID id, boolean active) {
        StoreSite store = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Store not found", HttpStatus.NOT_FOUND));
        StoreSiteDto.Response oldValue = toResponse(store);

        if (!active) {
            inUseValidator.validateStoreDeactivation(id);
        }

        store.setActive(active);
        store.setUpdatedBy(CurrentUserHolder.getUserId());
        store = repo.save(store);
        StoreSiteDto.Response newValue = toResponse(store);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "STORE_SITE", store.getId(), store.getStoreCode(), oldValue, newValue, null));
    }

    public PageResponse<StoreSiteDto.Response> list(String search, String storeType, Boolean active, Pageable pageable) {
        Page<StoreSite> page = repo.search(search, storeType, active, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public StoreSiteDto.Response getById(UUID id) {
        StoreSite store = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Store not found", HttpStatus.NOT_FOUND));
        return toResponse(store);
    }

    private StoreSiteDto.Response toResponse(StoreSite store) {
        return new StoreSiteDto.Response(
                store.getId(),
                store.getStoreCode(),
                store.getStoreName(),
                store.getOfficeLocationId(),
                store.getOfficeCodeSnapshot(),
                store.getOfficeNameSnapshot(),
                store.getAddress(),
                store.getStoreType(),
                store.isActive(),
                store.getCreatedAt(),
                store.getCreatedBy(),
                store.getUpdatedAt(),
                store.getUpdatedBy(),
                store.getVersion() != null ? store.getVersion() : 0L
        );
    }
}
