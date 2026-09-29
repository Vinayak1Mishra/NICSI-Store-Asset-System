package com.nicsi.store.inventory.controller;

import com.nicsi.store.common.idempotency.IdempotencyInterceptor;
import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.dto.StockAdjustmentDto;
import com.nicsi.store.inventory.service.StockAdjustmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentService adjustmentService;

    public StockAdjustmentController(StockAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_ADJUST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<StockAdjustmentDto.Response> createAdjustment(
            @Valid @RequestBody StockAdjustmentDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adjustmentService.createAdjustment(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<StockAdjustmentDto.SummaryResponse>> listAdjustments(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reasonCode,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(adjustmentService.search(storeId, status, reasonCode, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<StockAdjustmentDto.Response> getAdjustment(@PathVariable UUID id) {
        return ResponseEntity.ok(adjustmentService.getAdjustment(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_ADJUST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<StockAdjustmentDto.Response> submitAdjustment(@PathVariable UUID id) {
        return ResponseEntity.ok(adjustmentService.submitAdjustment(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_ADJUST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<StockAdjustmentDto.Response> approveAdjustment(@PathVariable UUID id) {
        return ResponseEntity.ok(adjustmentService.approveAdjustment(id));
    }

    @PostMapping("/{id}/post")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_ADJUST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<StockAdjustmentDto.Response> postAdjustment(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTRIBUTE);
        return ResponseEntity.ok(adjustmentService.postAdjustment(id, idempotencyKey));
    }

    @PostMapping("/{transactionId}/reversal")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_ADJUST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<StockAdjustmentDto.Response> reverseTransaction(
            @PathVariable UUID transactionId,
            @Valid @RequestBody StockAdjustmentDto.ReversalRequest request,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTRIBUTE);
        return ResponseEntity.ok(adjustmentService.reverseTransaction(transactionId, request.reason(), idempotencyKey));
    }
}
