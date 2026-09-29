package com.nicsi.store.procurementref.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.procurementref.dto.PurchaseOrderDto;
import com.nicsi.store.procurementref.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PurchaseOrderDto.Response> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.createPurchaseOrder(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<PurchaseOrderDto.SummaryResponse>> listPurchaseOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(purchaseOrderService.search(search, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PurchaseOrderDto.Response> getPurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrder(id));
    }

    @GetMapping("/by-number/{poNumber}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PurchaseOrderDto.Response> getPurchaseOrderByNumber(@PathVariable String poNumber) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderByNumber(poNumber));
    }
}
