package com.nicsi.store.inventory.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.dto.InventoryDto;
import com.nicsi.store.inventory.service.ReconciliationService;
import com.nicsi.store.inventory.service.ReservationService;
import com.nicsi.store.inventory.service.StockQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/inventory")
public class InventoryController {

    private final StockQueryService stockQueryService;
    private final ReservationService reservationService;
    private final ReconciliationService reconciliationService;

    public InventoryController(
            StockQueryService stockQueryService,
            ReservationService reservationService,
            ReconciliationService reconciliationService
    ) {
        this.stockQueryService = stockQueryService;
        this.reservationService = reservationService;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<InventoryDto.BalanceResponse>> listBalances(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean lowStockOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(stockQueryService.searchBalances(storeId, categoryId, search, lowStockOnly, pageable));
    }

    @GetMapping("/balances/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<InventoryDto.BalanceResponse> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(stockQueryService.getBalance(id));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<InventoryDto.LedgerResponse>> listLedger(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(stockQueryService.searchLedger(itemId, storeId, locationId, transactionType, fromDate, toDate, search, pageable));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<InventoryDto.LowStockResponse>> getLowStock(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(stockQueryService.getLowStock(pageable));
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<InventoryDto.ReservationResponse>> listReservations(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(reservationService.search(storeId, itemId, status, pageable));
    }

    @PostMapping("/reservations/{id}/release")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_ADJUST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<InventoryDto.ReservationResponse> releaseReservation(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Released by user") String reason
    ) {
        return ResponseEntity.ok(reservationService.releaseReservation(id, reason));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STOCK_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<InventoryDto.ReconciliationResponse> getReconciliation() {
        return ResponseEntity.ok(reconciliationService.reconcile());
    }
}
