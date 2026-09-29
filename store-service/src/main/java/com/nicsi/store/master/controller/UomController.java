package com.nicsi.store.master.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.dto.StatusRequest;
import com.nicsi.store.master.dto.UomDto;
import com.nicsi.store.master.service.UomService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/uoms")
public class UomController {

    private final UomService service;

    public UomController(UomService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<PageResponse<UomDto.Response>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(service.list(search, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<UomDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM_CREATE')")
    @IdempotentPost
    public ResponseEntity<UomDto.Response> create(@Valid @RequestBody UomDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<UomDto.Response> update(@PathVariable UUID id, @Valid @RequestBody UomDto.UpdateRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        service.updateStatus(id, req.active());
        return ResponseEntity.ok().build();
    }
}
