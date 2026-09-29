package com.nicsi.store.transfer.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.transfer.dto.TransferDto;
import com.nicsi.store.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.TRANSFER_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<TransferDto.Response> create(@Valid @RequestBody TransferDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.createTransfer(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.TRANSFER_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<TransferDto.Response> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.submitTransfer(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('" + Permissions.TRANSFER_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<TransferDto.Response> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.approveTransfer(id));
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyAuthority('" + Permissions.TRANSFER_DISPATCH + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<TransferDto.Response> dispatch(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.dispatchTransfer(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyAuthority('" + Permissions.TRANSFER_RECEIVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<TransferDto.Response> receive(@PathVariable UUID id,
            @Valid @RequestBody TransferDto.ReceiveRequest req) {
        return ResponseEntity.ok(transferService.receiveTransfer(id, req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<TransferDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transferService.getTransfer(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<TransferDto.SummaryResponse>> list(
            @RequestParam(required = false) UUID sourceStoreId,
            @RequestParam(required = false) UUID destinationStoreId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(transferService.search(sourceStoreId, destinationStoreId, status, pageable));
    }
}
