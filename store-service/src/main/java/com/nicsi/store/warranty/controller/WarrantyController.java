package com.nicsi.store.warranty.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.warranty.dto.WarrantyDto;
import com.nicsi.store.warranty.service.WarrantyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/warranties")
@PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
public class WarrantyController {

    private final WarrantyService warrantyService;
    public WarrantyController(WarrantyService warrantyService) { this.warrantyService = warrantyService; }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<WarrantyDto.Response> create(@Valid @RequestBody WarrantyDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warrantyService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<WarrantyDto.Response> update(@PathVariable UUID id,
            @RequestBody WarrantyDto.UpdateRequest req) {
        return ResponseEntity.ok(warrantyService.update(id, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarrantyDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(warrantyService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<WarrantyDto.Response>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assetId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(warrantyService.search(type, status, assetId, pageable));
    }
}
