package com.nicsi.store.master.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.dto.StatusRequest;
import com.nicsi.store.master.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<PageResponse<ItemDto.Response>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String trackingType,
            @RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return ResponseEntity.ok(service.list(search, categoryId, itemType, trackingType, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM_VIEW')")
    public ResponseEntity<ItemDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM_CREATE')")
    @IdempotentPost
    public ResponseEntity<ItemDto.Response> create(@Valid @RequestBody ItemDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<ItemDto.Response> update(@PathVariable UUID id, @Valid @RequestBody ItemDto.UpdateRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ITEM_UPDATE')")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        service.updateStatus(id, req.active());
        return ResponseEntity.ok().build();
    }
}
