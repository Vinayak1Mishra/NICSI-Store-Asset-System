package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.domain.ItemSubcategory;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.ItemSubcategoryRepository;
import com.nicsi.store.master.repository.UomRepository;
import com.nicsi.store.master.validation.MasterInUseValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository repo;
    private final ItemCategoryRepository categoryRepo;
    private final ItemSubcategoryRepository subcategoryRepo;
    private final UomRepository uomRepo;
    private final AuditService auditService;
    private final MasterInUseValidator inUseValidator;

    public ItemService(
            ItemRepository repo,
            ItemCategoryRepository categoryRepo,
            ItemSubcategoryRepository subcategoryRepo,
            UomRepository uomRepo,
            AuditService auditService,
            MasterInUseValidator inUseValidator) {
        this.repo = repo;
        this.categoryRepo = categoryRepo;
        this.subcategoryRepo = subcategoryRepo;
        this.uomRepo = uomRepo;
        this.auditService = auditService;
        this.inUseValidator = inUseValidator;
    }

    @Transactional
    public ItemDto.Response create(ItemDto.CreateRequest req) {
        if (repo.existsByItemCodeIgnoreCase(req.itemCode())) {
            throw new BusinessException("DUPLICATE_CODE", "Item code already exists", HttpStatus.CONFLICT);
        }
        
        validateBusinessRules(req.assetRequired(), req.trackingType());

        ItemCategory category = categoryRepo.findById(req.categoryId())
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));

        ItemSubcategory subcategory = null;
        if (req.subcategoryId() != null) {
            subcategory = subcategoryRepo.findById(req.subcategoryId())
                    .orElseThrow(() -> new BusinessException("SUBCATEGORY_NOT_FOUND", "Subcategory not found", HttpStatus.NOT_FOUND));
        }

        Uom uom = uomRepo.findById(req.baseUomId())
                .orElseThrow(() -> new BusinessException("UOM_NOT_FOUND", "UOM not found", HttpStatus.NOT_FOUND));

        Item item = new Item();
        item.setItemCode(req.itemCode());
        item.setItemName(req.itemName());
        item.setCategory(category);
        item.setSubcategory(subcategory);
        item.setBaseUom(uom);
        item.setItemType(req.itemType());
        item.setTrackingType(req.trackingType());
        item.setShortDescription(req.shortDescription());
        item.setSpecification(req.specification());
        item.setManufacturerDefault(req.manufacturerDefault());
        item.setModelDefault(req.modelDefault());
        item.setHsnSacCode(req.hsnSacCode());
        item.setStandardRate(req.standardRate());
        item.setUsefulLifeMonths(req.usefulLifeMonths());
        item.setWarrantyMonths(req.warrantyMonths());
        item.setReturnable(Boolean.TRUE.equals(req.returnable()));
        item.setWarrantyApplicable(Boolean.TRUE.equals(req.warrantyApplicable()));
        item.setExpiryTracking(Boolean.TRUE.equals(req.expiryTracking()));
        item.setAssetRequired(Boolean.TRUE.equals(req.assetRequired()));
        item.setActive(true);
        item.setCreatedBy(CurrentUserHolder.getUserId());
        item.setUpdatedBy(CurrentUserHolder.getUserId());

        item = repo.save(item);
        ItemDto.Response response = toResponse(item);

        auditService.record(new AuditEvent("STORE", "CREATE", "ITEM", item.getId(), item.getItemCode(), null, response, null));
        return response;
    }

    @Transactional
    public ItemDto.Response update(UUID id, ItemDto.UpdateRequest req) {
        Item item = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Item not found", HttpStatus.NOT_FOUND));
        if (req.version() != null && !req.version().equals(item.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Item was updated by another transaction", HttpStatus.CONFLICT);
        }
        ItemDto.Response oldValue = toResponse(item);

        validateBusinessRules(req.assetRequired(), req.trackingType());

        ItemCategory category = categoryRepo.findById(req.categoryId())
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));

        ItemSubcategory subcategory = null;
        if (req.subcategoryId() != null) {
            subcategory = subcategoryRepo.findById(req.subcategoryId())
                    .orElseThrow(() -> new BusinessException("SUBCATEGORY_NOT_FOUND", "Subcategory not found", HttpStatus.NOT_FOUND));
        }

        Uom uom = uomRepo.findById(req.baseUomId())
                .orElseThrow(() -> new BusinessException("UOM_NOT_FOUND", "UOM not found", HttpStatus.NOT_FOUND));

        item.setItemName(req.itemName());
        item.setCategory(category);
        item.setSubcategory(subcategory);
        item.setBaseUom(uom);
        item.setItemType(req.itemType());
        item.setTrackingType(req.trackingType());
        item.setShortDescription(req.shortDescription());
        item.setSpecification(req.specification());
        item.setManufacturerDefault(req.manufacturerDefault());
        item.setModelDefault(req.modelDefault());
        item.setHsnSacCode(req.hsnSacCode());
        item.setStandardRate(req.standardRate());
        item.setUsefulLifeMonths(req.usefulLifeMonths());
        item.setWarrantyMonths(req.warrantyMonths());
        item.setReturnable(Boolean.TRUE.equals(req.returnable()));
        item.setWarrantyApplicable(Boolean.TRUE.equals(req.warrantyApplicable()));
        item.setExpiryTracking(Boolean.TRUE.equals(req.expiryTracking()));
        item.setAssetRequired(Boolean.TRUE.equals(req.assetRequired()));
        item.setUpdatedBy(CurrentUserHolder.getUserId());
        item = repo.saveAndFlush(item);
        ItemDto.Response newValue = toResponse(item);

        auditService.record(new AuditEvent("STORE", "UPDATE", "ITEM", item.getId(), item.getItemCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID id, boolean active) {
        Item item = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Item not found", HttpStatus.NOT_FOUND));
        ItemDto.Response oldValue = toResponse(item);

        if (!active) {
            inUseValidator.validateItemDeactivation(id);
        }

        item.setActive(active);
        item.setUpdatedBy(CurrentUserHolder.getUserId());
        item = repo.save(item);
        ItemDto.Response newValue = toResponse(item);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "ITEM", item.getId(), item.getItemCode(), oldValue, newValue, null));
    }

    public PageResponse<ItemDto.Response> list(String search, UUID categoryId, String itemType, String trackingType, Boolean active, Pageable pageable) {
        Page<Item> page = repo.search(search, categoryId, itemType, trackingType, active, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public ItemDto.Response getById(UUID id) {
        Item item = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Item not found", HttpStatus.NOT_FOUND));
        return toResponse(item);
    }
    
    private void validateBusinessRules(Boolean assetRequired, String trackingType) {
        if (Boolean.TRUE.equals(assetRequired) && (trackingType == null || !trackingType.equals("SERIAL"))) {
            throw new BusinessException("INVALID_TRACKING_TYPE", "Items requiring asset must have SERIAL tracking type", HttpStatus.BAD_REQUEST);
        }
    }

    private ItemDto.Response toResponse(Item item) {
        return new ItemDto.Response(
                item.getId(),
                item.getItemCode(),
                item.getItemName(),
                item.getCategory().getId(),
                item.getCategory().getCategoryCode(),
                item.getCategory().getCategoryName(),
                item.getSubcategory() != null ? item.getSubcategory().getId() : null,
                item.getSubcategory() != null ? item.getSubcategory().getSubcategoryCode() : null,
                item.getSubcategory() != null ? item.getSubcategory().getSubcategoryName() : null,
                item.getBaseUom().getId(),
                item.getBaseUom().getUomCode(),
                item.getBaseUom().getUomName(),
                item.getItemType(),
                item.getTrackingType(),
                item.getShortDescription(),
                item.getSpecification(),
                item.getManufacturerDefault(),
                item.getModelDefault(),
                item.getHsnSacCode(),
                item.getStandardRate(),
                item.getUsefulLifeMonths(),
                item.getWarrantyMonths(),
                item.isReturnable(),
                item.isWarrantyApplicable(),
                item.isExpiryTracking(),
                item.isAssetRequired(),
                item.isActive(),
                item.getCreatedAt(),
                item.getCreatedBy(),
                item.getUpdatedAt(),
                item.getUpdatedBy(),
                item.getVersion() != null ? item.getVersion() : 0L
        );
    }
}
