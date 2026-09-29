package com.nicsi.store.disposal.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.disposal.dto.DisposalDto;
import com.nicsi.store.disposal.service.DisposalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/disposals")
public class DisposalController {

    private final DisposalService disposalService;

    public DisposalController(DisposalService disposalService) {
        this.disposalService = disposalService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.DISPOSAL_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<DisposalDto.Response> create(@Valid @RequestBody DisposalDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disposalService.create(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.DISPOSAL_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<DisposalDto.Response> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(disposalService.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('" + Permissions.DISPOSAL_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<DisposalDto.Response> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(disposalService.approve(id));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyAuthority('" + Permissions.DISPOSAL_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<DisposalDto.Response> post(@PathVariable UUID id) {
        return ResponseEntity.ok(disposalService.post(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<DisposalDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(disposalService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<DisposalDto.Response>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disposalService.search(status, method, pageable));
    }
}
