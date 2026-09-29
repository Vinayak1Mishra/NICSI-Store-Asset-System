package com.nicsi.store.verification.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.verification.dto.VerificationDto;
import com.nicsi.store.verification.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.VERIFICATION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<VerificationDto.Response> create(@Valid @RequestBody VerificationDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(verificationService.createVerification(req));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyAuthority('" + Permissions.VERIFICATION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<VerificationDto.Response> start(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.startVerification(id));
    }

    @PostMapping("/{id}/count")
    @PreAuthorize("hasAnyAuthority('" + Permissions.VERIFICATION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<VerificationDto.Response> recordCount(
            @PathVariable UUID id,
            @Valid @RequestBody VerificationDto.RecordCountRequest req) {
        return ResponseEntity.ok(verificationService.recordCount(id, req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.VERIFICATION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<VerificationDto.Response> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.submitVerification(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('" + Permissions.VERIFICATION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<VerificationDto.Response> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.approveVerification(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<VerificationDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.getVerification(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<List<VerificationDto.ItemResponse>> getItems(@PathVariable UUID id) {
        return ResponseEntity.ok(verificationService.getItems(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<VerificationDto.Response>> list(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(verificationService.search(storeId, status, pageable));
    }
}
