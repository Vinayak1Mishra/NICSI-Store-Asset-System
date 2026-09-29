package com.nicsi.store.inspection.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.inspection.service.InspectionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.INSPECTION_CREATE + "', '" + Permissions.INSPECTION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<InspectionDto.SummaryResponse>> listInspections(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(inspectionService.search(status, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.INSPECTION_CREATE + "', '" + Permissions.INSPECTION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<InspectionDto.Response> getInspection(@PathVariable UUID id) {
        return ResponseEntity.ok(inspectionService.getInspection(id));
    }

    @GetMapping("/by-grn/{grnId}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.INSPECTION_CREATE + "', '" + Permissions.INSPECTION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<InspectionDto.Response> getInspectionByGrnId(@PathVariable UUID grnId) {
        return ResponseEntity.ok(inspectionService.getInspectionByGrnId(grnId));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAnyAuthority('" + Permissions.INSPECTION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<InspectionDto.Response> recordDecision(
            @PathVariable UUID id,
            @Valid @RequestBody InspectionDto.DecideRequest request
    ) {
        return ResponseEntity.ok(inspectionService.recordDecision(id, request));
    }
}
