package com.nicsi.store.asset.controller;

import com.nicsi.store.asset.dto.AssetDto;
import com.nicsi.store.asset.service.AssetRegistrationService;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/store/assets", "/api/assets"})
public class AssetController {

    private final AssetRegistrationService assetService;

    public AssetController(AssetRegistrationService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<AssetDto.SummaryResponse>> listAssets(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String assetStatus,
            @RequestParam(required = false) String conditionStatus,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(assetService.search(storeId, itemId, assetStatus, conditionStatus, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> getAsset(@PathVariable UUID id) {
        return ResponseEntity.ok(assetService.getAsset(id));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> assignAsset(
            @PathVariable UUID id,
            @RequestBody AssetDto.AssignRequest request
    ) {
        return ResponseEntity.ok(assetService.assignAsset(id, request));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_TRANSFER + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> transferAsset(
            @PathVariable UUID id,
            @RequestBody AssetDto.TransferRequest request
    ) {
        return ResponseEntity.ok(assetService.transferAsset(id, request));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_ASSIGN + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> returnAsset(
            @PathVariable UUID id,
            @RequestBody AssetDto.ReturnRequest request
    ) {
        return ResponseEntity.ok(assetService.returnAsset(id, request));
    }

    @PostMapping("/{id}/repair")
    @PreAuthorize("hasAnyAuthority('" + Permissions.REPAIR_MANAGE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> repairAsset(
            @PathVariable UUID id,
            @RequestBody AssetDto.RepairRequest request
    ) {
        return ResponseEntity.ok(assetService.repairAsset(id, request));
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize("hasAnyAuthority('" + Permissions.DISPOSAL_APPROVE + "', '" + Permissions.DISPOSAL_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> disposeAsset(
            @PathVariable UUID id,
            @RequestBody AssetDto.DisposeRequest request
    ) {
        return ResponseEntity.ok(assetService.disposeAsset(id, request));
    }

    /**
     * The {*assetCode} capture-the-rest syntax is load-bearing, not decoration. Asset codes are
     * generated as NICSI/&lt;category-code&gt;/&lt;year&gt;/&lt;6-digit-sequence&gt; (AssetCodeGenerator.java:8),
     * so they contain slashes and a plain {assetCode} stops matching at the first "/". The request
     * then falls through to the static resource handler and surfaces as a 500.
     */
    @GetMapping("/by-code/{*assetCode}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ASSET_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<AssetDto.Response> getAssetByCode(@PathVariable String assetCode) {
        String code = assetCode.startsWith("/") ? assetCode.substring(1) : assetCode;
        return ResponseEntity.ok(assetService.getAssetByCode(code));
    }
}
