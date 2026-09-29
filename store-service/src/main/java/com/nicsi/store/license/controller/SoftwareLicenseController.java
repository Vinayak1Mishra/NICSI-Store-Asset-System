package com.nicsi.store.license.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.license.dto.SoftwareLicenseDto;
import com.nicsi.store.license.service.SoftwareLicenseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/store/licenses", "/api/licenses"})
public class SoftwareLicenseController {

    private final SoftwareLicenseService licenseService;

    public SoftwareLicenseController(SoftwareLicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ITEM_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<SoftwareLicenseDto.SummaryResponse>> listLicenses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String licenseType,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(licenseService.search(status, licenseType, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ITEM_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<SoftwareLicenseDto.Response> getLicense(@PathVariable UUID id) {
        return ResponseEntity.ok(licenseService.getLicense(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ITEM_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<SoftwareLicenseDto.Response> createLicense(
            @Valid @RequestBody SoftwareLicenseDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(licenseService.createLicense(request));
    }

    @PostMapping("/intake-from-grn/{grnItemId}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<SoftwareLicenseDto.Response> intakeFromGrn(
            @PathVariable UUID grnItemId,
            @Valid @RequestBody SoftwareLicenseDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(licenseService.intakeFromGrn(grnItemId, request));
    }

    @PostMapping("/{id}/allocate")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.ITEM_UPDATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<SoftwareLicenseDto.Response> allocateLicense(
            @PathVariable UUID id,
            @Valid @RequestBody SoftwareLicenseDto.AllocateRequest request
    ) {
        return ResponseEntity.ok(licenseService.allocateLicense(id, request));
    }

    @PostMapping("/{id}/allocations/{allocationId}/release")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.ITEM_UPDATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<SoftwareLicenseDto.Response> releaseAllocation(
            @PathVariable UUID id,
            @PathVariable UUID allocationId,
            @RequestParam(required = false) String remarks
    ) {
        return ResponseEntity.ok(licenseService.releaseAllocation(id, allocationId, remarks));
    }
}
