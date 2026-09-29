package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.dto.ItemCategoryDto;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.validation.MasterInUseValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ItemCategoryService {

    private final ItemCategoryRepository repo;
    private final AuditService auditService;
    private final MasterInUseValidator inUseValidator;

    public ItemCategoryService(ItemCategoryRepository repo, AuditService auditService, MasterInUseValidator inUseValidator) {
        this.repo = repo;
        this.auditService = auditService;
        this.inUseValidator = inUseValidator;
    }

    @Transactional
    public ItemCategoryDto.Response create(ItemCategoryDto.CreateRequest req) {
        if (repo.existsByCategoryCodeIgnoreCase(req.categoryCode())) {
            throw new BusinessException("DUPLICATE_CODE", "Category code already exists", HttpStatus.CONFLICT);
        }

        ItemCategory category = new ItemCategory();
        category.setCategoryCode(req.categoryCode());
        category.setCategoryName(req.categoryName());
        category.setDescription(req.description());
        category.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        category.setActive(true);
        category.setCreatedBy(CurrentUserHolder.getUserId());
        category.setUpdatedBy(CurrentUserHolder.getUserId());

        category = repo.save(category);
        ItemCategoryDto.Response response = toResponse(category);

        auditService.record(new AuditEvent("STORE", "CREATE", "ITEM_CATEGORY", category.getId(), category.getCategoryCode(), null, response, null));
        return response;
    }

    @Transactional
    public ItemCategoryDto.Response update(UUID id, ItemCategoryDto.UpdateRequest req) {
        ItemCategory category = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        if (req.version() != null && !req.version().equals(category.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "Category was updated by another transaction", HttpStatus.CONFLICT);
        }
        ItemCategoryDto.Response oldValue = toResponse(category);

        category.setCategoryName(req.categoryName());
        category.setDescription(req.description());
        category.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        category.setUpdatedBy(CurrentUserHolder.getUserId());

        category = repo.saveAndFlush(category);
        ItemCategoryDto.Response newValue = toResponse(category);

        auditService.record(new AuditEvent("STORE", "UPDATE", "ITEM_CATEGORY", category.getId(), category.getCategoryCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID id, boolean active) {
        ItemCategory category = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        ItemCategoryDto.Response oldValue = toResponse(category);

        if (!active) {
            inUseValidator.validateCategoryDeactivation(id);
        }

        category.setActive(active);
        category.setUpdatedBy(CurrentUserHolder.getUserId());
        category = repo.save(category);
        ItemCategoryDto.Response newValue = toResponse(category);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "ITEM_CATEGORY", category.getId(), category.getCategoryCode(), oldValue, newValue, null));
    }

    public PageResponse<ItemCategoryDto.Response> list(String search, Boolean active, Pageable pageable) {
        Page<ItemCategory> page;
        if (search != null && !search.trim().isEmpty()) {
            page = repo.findByCategoryCodeContainingIgnoreCaseOrCategoryNameContainingIgnoreCase(search.trim(), search.trim(), pageable);
        } else if (Boolean.TRUE.equals(active)) {
            page = repo.findByActiveTrue(pageable);
        } else {
            page = repo.findAll(pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    public ItemCategoryDto.Response getById(UUID id) {
        ItemCategory category = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
        return toResponse(category);
    }

    private ItemCategoryDto.Response toResponse(ItemCategory category) {
        return new ItemCategoryDto.Response(
            category.getId(),
            category.getCategoryCode(),
            category.getCategoryName(),
            category.getDescription(),
            category.getSortOrder(),
            category.isActive(),
            category.getCreatedAt(),
            category.getCreatedBy(),
            category.getUpdatedAt(),
            category.getUpdatedBy(),
            category.getVersion() != null ? category.getVersion() : 0L
        );
    }
}
