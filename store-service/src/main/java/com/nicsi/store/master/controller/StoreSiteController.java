package com.nicsi.store.master.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.dto.StatusRequest;
import com.nicsi.store.master.dto.StoreSiteDto;
import com.nicsi.store.master.service.StoreSiteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/stores")
public class StoreSiteController {

    private final StoreSiteService service;

    public StoreSiteController(StoreSiteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('STORE_ADMIN','STOCK_VIEW')")
    public ResponseEntity<PageResponse<StoreSiteDto.Response>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String storeType,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(service.list(search, storeType, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('STORE_ADMIN','STOCK_VIEW')")
    public ResponseEntity<StoreSiteDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    @IdempotentPost
    public ResponseEntity<StoreSiteDto.Response> create(@Valid @RequestBody StoreSiteDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    public ResponseEntity<StoreSiteDto.Response> update(@PathVariable UUID id, @Valid @RequestBody StoreSiteDto.UpdateRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        service.updateStatus(id, req.active());
        return ResponseEntity.ok().build();
    }
}
