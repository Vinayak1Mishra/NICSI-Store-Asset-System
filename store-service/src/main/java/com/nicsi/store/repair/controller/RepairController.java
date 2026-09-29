package com.nicsi.store.repair.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.repair.dto.RepairDto;
import com.nicsi.store.repair.service.RepairService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/repairs")
@PreAuthorize("hasAnyAuthority('" + Permissions.REPAIR_MANAGE + "', '" + Permissions.STORE_ADMIN + "')")
public class RepairController {

    private final RepairService repairService;
    public RepairController(RepairService repairService) { this.repairService = repairService; }

    @PostMapping
    public ResponseEntity<RepairDto.Response> create(@Valid @RequestBody RepairDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repairService.createTicket(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RepairDto.Response> update(@PathVariable UUID id,
            @Valid @RequestBody RepairDto.UpdateRequest req) {
        return ResponseEntity.ok(repairService.updateTicket(id, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(repairService.getTicket(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<RepairDto.SummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assetId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(repairService.search(status, assetId, pageable));
    }
}
