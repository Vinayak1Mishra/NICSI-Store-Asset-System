package com.nicsi.store.master.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.dto.ItemCategoryDto;
import com.nicsi.store.master.dto.ItemSubcategoryDto;
import com.nicsi.store.master.dto.StatusRequest;
import com.nicsi.store.master.service.ItemCategoryService;
import com.nicsi.store.master.service.ItemSubcategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/categories")
public class ItemCategoryController {

    private final ItemCategoryService categoryService;
    private final ItemSubcategoryService subcategoryService;

    public ItemCategoryController(ItemCategoryService categoryService, ItemSubcategoryService subcategoryService) {
        this.categoryService = categoryService;
        this.subcategoryService = subcategoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<PageResponse<ItemCategoryDto.Response>> listCategories(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(categoryService.list(search, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<ItemCategoryDto.Response> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM_CREATE')")
    @IdempotentPost
    public ResponseEntity<ItemCategoryDto.Response> createCategory(@Valid @RequestBody ItemCategoryDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<ItemCategoryDto.Response> updateCategory(@PathVariable UUID id, @Valid @RequestBody ItemCategoryDto.UpdateRequest req) {
        return ResponseEntity.ok(categoryService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<Void> updateCategoryStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        categoryService.updateStatus(id, req.active());
        return ResponseEntity.ok().build();
    }

    // Subcategory endpoints
    @GetMapping("/{categoryId}/subcategories")
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<PageResponse<ItemSubcategoryDto.Response>> listSubcategories(
            @PathVariable UUID categoryId,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(subcategoryService.list(categoryId, active, pageable));
    }

    @PostMapping("/{categoryId}/subcategories")
    @PreAuthorize("hasAuthority('ITEM_CREATE')")
    @IdempotentPost
    public ResponseEntity<ItemSubcategoryDto.Response> createSubcategory(@PathVariable UUID categoryId, @Valid @RequestBody ItemSubcategoryDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subcategoryService.create(categoryId, req));
    }

    @PutMapping("/{categoryId}/subcategories/{id}")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<ItemSubcategoryDto.Response> updateSubcategory(@PathVariable UUID categoryId, @PathVariable UUID id, @Valid @RequestBody ItemSubcategoryDto.UpdateRequest req) {
        return ResponseEntity.ok(subcategoryService.update(categoryId, id, req));
    }

    @PatchMapping("/{categoryId}/subcategories/{id}/status")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<Void> updateSubcategoryStatus(@PathVariable UUID categoryId, @PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        subcategoryService.updateStatus(categoryId, id, req.active());
        return ResponseEntity.ok().build();
    }
}
