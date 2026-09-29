package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.domain.ItemSubcategory;
import com.nicsi.store.master.dto.ItemSubcategoryDto;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemSubcategoryRepository;
import com.nicsi.store.master.validation.MasterInUseValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ItemSubcategoryService {

    private final ItemSubcategoryRepository repo;
    private final ItemCategoryRepository categoryRepo;
    private final AuditService auditService;
    private final MasterInUseValidator inUseValidator;

    public ItemSubcategoryService(
            ItemSubcategoryRepository repo,
            ItemCategoryRepository categoryRepo,
            AuditService auditService,
            MasterInUseValidator inUseValidator) {
        this.repo = repo;
        this.categoryRepo = categoryRepo;
        this.auditService = auditService;
        this.inUseValidator = inUseValidator;
    }

    @Transactional
    public ItemSubcategoryDto.Response create(UUID categoryId, ItemSubcategoryDto.CreateRequest req) {
        ItemCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));

        if (repo.existsByCategoryIdAndSubcategoryCodeIgnoreCase(categoryId, req.subcategoryCode())) {
            throw new BusinessException("DUPLICATE_CODE", "Subcategory code already exists in this category", HttpStatus.CONFLICT);
        }

        ItemSubcategory subcat = new ItemSubcategory();
        subcat.setCategory(category);
        subcat.setSubcategoryCode(req.subcategoryCode());
        subcat.setSubcategoryName(req.subcategoryName());
        subcat.setDescription(req.description());
        subcat.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        subcat.setActive(true);
        subcat.setCreatedBy(CurrentUserHolder.getUserId());
        subcat.setUpdatedBy(CurrentUserHolder.getUserId());

        subcat = repo.save(subcat);
        ItemSubcategoryDto.Response response = toResponse(subcat);

        auditService.record(new AuditEvent("STORE", "CREATE", "ITEM_SUBCATEGORY", subcat.getId(), subcat.getSubcategoryCode(), null, response, null));
        return response;
    }

    @Transactional
    public ItemSubcategoryDto.Response update(UUID categoryId, UUID id, ItemSubcategoryDto.UpdateRequest req) {
        ItemSubcategory subcat = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Subcategory not found", HttpStatus.NOT_FOUND));
        if (!subcat.getCategory().getId().equals(categoryId)) {
            throw new BusinessException("INVALID_SUBCATEGORY", "Subcategory does not belong to this category", HttpStatus.BAD_REQUEST);
        }
        if (req.version() != null && !req.version().equals(subcat.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Subcategory was updated by another transaction", HttpStatus.CONFLICT);
        }
        ItemSubcategoryDto.Response oldValue = toResponse(subcat);

        subcat.setSubcategoryName(req.subcategoryName());
        subcat.setDescription(req.description());
        subcat.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        subcat.setUpdatedBy(CurrentUserHolder.getUserId());

        subcat = repo.saveAndFlush(subcat);
        ItemSubcategoryDto.Response newValue = toResponse(subcat);

        auditService.record(new AuditEvent("STORE", "UPDATE", "ITEM_SUBCATEGORY", subcat.getId(), subcat.getSubcategoryCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID categoryId, UUID id, boolean active) {
        ItemSubcategory subcat = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Subcategory not found", HttpStatus.NOT_FOUND));
        if (!subcat.getCategory().getId().equals(categoryId)) {
            throw new BusinessException("INVALID_SUBCATEGORY", "Subcategory does not belong to this category", HttpStatus.BAD_REQUEST);
        }
        ItemSubcategoryDto.Response oldValue = toResponse(subcat);

        if (!active) {
            inUseValidator.validateSubcategoryDeactivation(id);
        }

        subcat.setActive(active);
        subcat.setUpdatedBy(CurrentUserHolder.getUserId());
        subcat = repo.save(subcat);
        ItemSubcategoryDto.Response newValue = toResponse(subcat);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "ITEM_SUBCATEGORY", subcat.getId(), subcat.getSubcategoryCode(), oldValue, newValue, null));
    }

    public PageResponse<ItemSubcategoryDto.Response> list(UUID categoryId, Boolean active, Pageable pageable) {
        Page<ItemSubcategory> page;
        if (Boolean.TRUE.equals(active)) {
            page = repo.findByCategoryIdAndActiveTrue(categoryId, pageable);
        } else {
            page = repo.findByCategoryId(categoryId, pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    public ItemSubcategoryDto.Response getById(UUID categoryId, UUID id) {
        ItemSubcategory subcat = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Subcategory not found", HttpStatus.NOT_FOUND));
        if (!subcat.getCategory().getId().equals(categoryId)) {
            throw new BusinessException("INVALID_SUBCATEGORY", "Subcategory does not belong to this category", HttpStatus.BAD_REQUEST);
        }
        return toResponse(subcat);
    }

    private ItemSubcategoryDto.Response toResponse(ItemSubcategory subcat) {
        return new ItemSubcategoryDto.Response(
            subcat.getId(),
            subcat.getCategory().getId(),
            subcat.getCategory().getCategoryCode(),
            subcat.getSubcategoryCode(),
            subcat.getSubcategoryName(),
            subcat.getDescription(),
            subcat.getSortOrder(),
            subcat.isActive(),
            subcat.getCreatedAt(),
            subcat.getCreatedBy(),
            subcat.getUpdatedAt(),
            subcat.getUpdatedBy(),
            subcat.getVersion() != null ? subcat.getVersion() : 0L
        );
    }
}
