package com.nicsi.store.master.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.dto.StatusRequest;
import com.nicsi.store.master.dto.StorageLocationDto;
import com.nicsi.store.master.service.StorageLocationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/locations")
public class StorageLocationController {

    private final StorageLocationService service;

    public StorageLocationController(StorageLocationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('STORE_ADMIN','STOCK_VIEW')")
    public ResponseEntity<PageResponse<StorageLocationDto.Response>> list(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID parentLocationId,
            @RequestParam(required = false) String locationType,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(service.list(storeId, parentLocationId, locationType, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('STORE_ADMIN','STOCK_VIEW')")
    public ResponseEntity<StorageLocationDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('STORE_ADMIN','STOCK_VIEW')")
    public ResponseEntity<List<StorageLocationDto.TreeResponse>> getTree(@RequestParam UUID storeId) {
        return ResponseEntity.ok(service.getTree(storeId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    @IdempotentPost
    public ResponseEntity<StorageLocationDto.Response> create(@Valid @RequestBody StorageLocationDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    public ResponseEntity<StorageLocationDto.Response> update(@PathVariable UUID id, @Valid @RequestBody StorageLocationDto.UpdateRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        service.updateStatus(id, req.active());
        return ResponseEntity.ok().build();
    }
}
