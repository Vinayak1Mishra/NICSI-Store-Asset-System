package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemStorePolicy;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.dto.ItemStorePolicyDto;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.ItemStorePolicyRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ItemStorePolicyService {

    private final ItemStorePolicyRepository repo;
    private final ItemRepository itemRepo;
    private final StoreSiteRepository storeRepo;
    private final StorageLocationRepository locationRepo;
    private final AuditService auditService;

    public ItemStorePolicyService(
            ItemStorePolicyRepository repo,
            ItemRepository itemRepo,
            StoreSiteRepository storeRepo,
            StorageLocationRepository locationRepo,
            AuditService auditService) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.storeRepo = storeRepo;
        this.locationRepo = locationRepo;
        this.auditService = auditService;
    }

    @Transactional
    public ItemStorePolicyDto.Response create(ItemStorePolicyDto.CreateRequest req) {
        if (repo.existsByItemIdAndStoreId(req.itemId(), req.storeId())) {
            throw new BusinessException("DUPLICATE_POLICY", "Policy already exists for this item and store", HttpStatus.CONFLICT);
        }

        Item item = itemRepo.findById(req.itemId())
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found", HttpStatus.NOT_FOUND));
        if (!item.isActive()) {
            throw new BusinessException("ITEM_INACTIVE", "Item is not active", HttpStatus.BAD_REQUEST);
        }

        StoreSite store = storeRepo.findById(req.storeId())
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND", "Store not found", HttpStatus.NOT_FOUND));
        if (!store.isActive()) {
            throw new BusinessException("STORE_INACTIVE", "Store is not active", HttpStatus.BAD_REQUEST);
        }
        
        validateMinMax(req.minStockQty(), req.maxStockQty());

        StorageLocation defLocation = null;
        if (req.defaultLocationId() != null) {
            defLocation = locationRepo.findById(req.defaultLocationId()).orElse(null);
        }

        ItemStorePolicy policy = new ItemStorePolicy();
        policy.setItem(item);
        policy.setStore(store);
        policy.setMinStockQty(req.minStockQty());
        policy.setMaxStockQty(req.maxStockQty());
        policy.setReorderLevelQty(req.reorderLevelQty());
        policy.setReorderQty(req.reorderQty());
        policy.setAllowNegativeStock(Boolean.TRUE.equals(req.allowNegativeStock()));
        policy.setValuationMethod(req.valuationMethod());
        policy.setDefaultLocation(defLocation);
        policy.setActive(true);
        policy.setCreatedBy(CurrentUserHolder.getUserId());
        policy.setUpdatedBy(CurrentUserHolder.getUserId());

        policy = repo.save(policy);
        ItemStorePolicyDto.Response response = toResponse(policy);

        auditService.record(new AuditEvent("STORE", "CREATE", "ITEM_STORE_POLICY", policy.getId(), item.getItemCode() + "-" + store.getStoreCode(), null, response, null));
        return response;
    }

    @Transactional
    public ItemStorePolicyDto.Response update(UUID id, ItemStorePolicyDto.UpdateRequest req) {
        ItemStorePolicy policy = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Policy not found", HttpStatus.NOT_FOUND));
        if (req.version() != null && !req.version().equals(policy.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Policy was updated by another transaction", HttpStatus.CONFLICT);
        }
        ItemStorePolicyDto.Response oldValue = toResponse(policy);

        validateMinMax(req.minStockQty(), req.maxStockQty());

        StorageLocation defLocation = null;
        if (req.defaultLocationId() != null) {
            defLocation = locationRepo.findById(req.defaultLocationId()).orElse(null);
        }

        policy.setMinStockQty(req.minStockQty());
        policy.setMaxStockQty(req.maxStockQty());
        policy.setReorderLevelQty(req.reorderLevelQty());
        policy.setReorderQty(req.reorderQty());
        policy.setAllowNegativeStock(Boolean.TRUE.equals(req.allowNegativeStock()));
        policy.setValuationMethod(req.valuationMethod());
        policy.setDefaultLocation(defLocation);
        policy.setUpdatedBy(CurrentUserHolder.getUserId());

        policy = repo.saveAndFlush(policy);
        ItemStorePolicyDto.Response newValue = toResponse(policy);

        auditService.record(new AuditEvent("STORE", "UPDATE", "ITEM_STORE_POLICY", policy.getId(), policy.getItem().getItemCode() + "-" + policy.getStore().getStoreCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID id, boolean active) {
        ItemStorePolicy policy = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Policy not found", HttpStatus.NOT_FOUND));
        ItemStorePolicyDto.Response oldValue = toResponse(policy);

        policy.setActive(active);
        policy.setUpdatedBy(CurrentUserHolder.getUserId());
        policy = repo.save(policy);
        ItemStorePolicyDto.Response newValue = toResponse(policy);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "ITEM_STORE_POLICY", policy.getId(), policy.getItem().getItemCode() + "-" + policy.getStore().getStoreCode(), oldValue, newValue, null));
    }

    public PageResponse<ItemStorePolicyDto.Response> list(UUID itemId, UUID storeId, Boolean active, Pageable pageable) {
        Page<ItemStorePolicy> page = repo.search(itemId, storeId, active, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public ItemStorePolicyDto.Response getById(UUID id) {
        ItemStorePolicy policy = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Policy not found", HttpStatus.NOT_FOUND));
        return toResponse(policy);
    }
    
    private void validateMinMax(java.math.BigDecimal min, java.math.BigDecimal max) {
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new BusinessException("INVALID_MIN_MAX", "Max stock quantity cannot be less than min stock quantity", HttpStatus.BAD_REQUEST);
        }
    }

    private ItemStorePolicyDto.Response toResponse(ItemStorePolicy policy) {
        return new ItemStorePolicyDto.Response(
                policy.getId(),
                policy.getItem().getId(),
                policy.getItem().getItemCode(),
                policy.getItem().getItemName(),
                policy.getStore().getId(),
                policy.getStore().getStoreCode(),
                policy.getStore().getStoreName(),
                policy.getMinStockQty(),
                policy.getMaxStockQty(),
                policy.getReorderLevelQty(),
                policy.getReorderQty(),
                policy.isAllowNegativeStock(),
                policy.getValuationMethod(),
                policy.getDefaultLocation() != null ? policy.getDefaultLocation().getId() : null,
                policy.isActive(),
                policy.getCreatedAt(),
                policy.getCreatedBy(),
                policy.getUpdatedAt(),
                policy.getUpdatedBy(),
                policy.getVersion() != null ? policy.getVersion() : 0L
        );
    }
}
