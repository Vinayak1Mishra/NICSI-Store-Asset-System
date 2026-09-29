package com.nicsi.store.condemnation.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.condemnation.dto.CondemnationDto;
import com.nicsi.store.condemnation.service.CondemnationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/condemnations")
public class CondemnationController {

    private final CondemnationService condemnationService;

    public CondemnationController(CondemnationService condemnationService) {
        this.condemnationService = condemnationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<CondemnationDto.Response> create(@Valid @RequestBody CondemnationDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(condemnationService.create(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<CondemnationDto.Response> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(condemnationService.submit(id));
    }

    @PostMapping("/{id}/recommend")
    @PreAuthorize("hasAnyAuthority('" + Permissions.CONDEMNATION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<CondemnationDto.Response> recommend(@PathVariable UUID id) {
        return ResponseEntity.ok(condemnationService.recommend(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('" + Permissions.CONDEMNATION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<CondemnationDto.Response> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(condemnationService.approve(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<CondemnationDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(condemnationService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<CondemnationDto.Response>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(condemnationService.search(status, pageable));
    }
}
