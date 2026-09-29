package com.nicsi.store.reporting.controller;

import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 8 — Reporting & Analytics API.
 * All endpoints are backed by purpose-built database views (vw_*) for performance.
 * Pagination is handled in-DB via LIMIT/OFFSET derived from Spring Pageable.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyAuthority('" + Permissions.REPORT_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
public class ReportController {

    private final NamedParameterJdbcTemplate jdbc;

    public ReportController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STOCK REPORTS
    // ─────────────────────────────────────────────────────────────────────────

    /** Current stock positions — filterable by store, item, or item type */
    @GetMapping("/stock/current")
    public ResponseEntity<List<Map<String, Object>>> getCurrentStock(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String itemType
    ) {
        StringBuilder sql = new StringBuilder("SELECT * FROM store.vw_current_stock WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (storeId != null)  { sql.append(" AND store_id = :storeId");   p.addValue("storeId", storeId); }
        if (itemId  != null)  { sql.append(" AND item_id = :itemId");     p.addValue("itemId", itemId); }
        if (itemType != null && !itemType.isBlank()) {
            sql.append(" AND item_type = :itemType");
            p.addValue("itemType", itemType.toUpperCase());
        }
        sql.append(" ORDER BY store_name, item_name");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), p));
    }

    /** Low-stock / reorder alert items */
    @GetMapping("/stock/low")
    public ResponseEntity<List<Map<String, Object>>> getLowStock(
            @RequestParam(required = false) UUID storeId
    ) {
        String sql = storeId != null
                ? "SELECT * FROM store.vw_low_stock WHERE store_id = :storeId ORDER BY total_available_qty ASC"
                : "SELECT * FROM store.vw_low_stock ORDER BY total_available_qty ASC";
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (storeId != null) p.addValue("storeId", storeId);
        return ResponseEntity.ok(jdbc.queryForList(sql, p));
    }

    /** Paginated stock ledger — filterable by store, item, type and date range */
    @GetMapping("/stock/ledger")
    public ResponseEntity<PageResponse<Map<String, Object>>> getStockLedger(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (storeId != null)          { where.append(" AND store_id = :storeId");              p.addValue("storeId", storeId); }
        if (itemId  != null)          { where.append(" AND item_id = :itemId");                p.addValue("itemId", itemId); }
        if (transactionType != null && !transactionType.isBlank()) {
            where.append(" AND transaction_type = :txType");
            p.addValue("txType", transactionType.toUpperCase());
        }
        if (from != null) { where.append(" AND transaction_date >= :from"); p.addValue("from", from); }
        if (to   != null) { where.append(" AND transaction_date <= :to");   p.addValue("to", to); }

        long total = countView("store.vw_stock_ledger", where, p);
        p.addValue("limit",  pageable.getPageSize());
        p.addValue("offset", pageable.getOffset());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM store.vw_stock_ledger" + where + " ORDER BY transaction_date DESC LIMIT :limit OFFSET :offset", p);

        int totalPages = (int) Math.ceil((double) total / pageable.getPageSize());
        return ResponseEntity.ok(new PageResponse<>(rows, pageable.getPageNumber(),
                pageable.getPageSize(), total, totalPages,
                pageable.getPageNumber() == 0, pageable.getPageNumber() >= totalPages - 1));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASSET REPORTS
    // ─────────────────────────────────────────────────────────────────────────

    /** Assets currently assigned to employees */
    @GetMapping("/assets/employee")
    public ResponseEntity<List<Map<String, Object>>> getEmployeeAssets(
            @RequestParam(required = false) UUID employeeId
    ) {
        String sql = employeeId != null
                ? "SELECT * FROM store.vw_employee_assets WHERE employee_id = :empId ORDER BY employee_name, item_name"
                : "SELECT * FROM store.vw_employee_assets ORDER BY employee_name, item_name";
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (employeeId != null) p.addValue("empId", employeeId);
        return ResponseEntity.ok(jdbc.queryForList(sql, p));
    }

    /** Assets with warranty expiring within N days (default 90) */
    @GetMapping("/assets/warranty-expiry")
    public ResponseEntity<List<Map<String, Object>>> getWarrantyExpiry(
            @RequestParam(defaultValue = "90") int withinDays
    ) {
        String sql = "SELECT * FROM store.vw_warranty_expiry WHERE days_to_expiry BETWEEN 0 AND :days ORDER BY days_to_expiry ASC";
        return ResponseEntity.ok(jdbc.queryForList(sql, new MapSqlParameterSource("days", withinDays)));
    }

    /** Disposal register — filterable by method and date range */
    @GetMapping("/assets/disposal")
    public ResponseEntity<List<Map<String, Object>>> getDisposals(
            @RequestParam(required = false) String disposalMethod,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (disposalMethod != null && !disposalMethod.isBlank()) {
            where.append(" AND disposal_method = :method");
            p.addValue("method", disposalMethod.toUpperCase());
        }
        if (from != null) { where.append(" AND disposal_date >= :from"); p.addValue("from", from); }
        if (to   != null) { where.append(" AND disposal_date <= :to");   p.addValue("to", to); }
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT * FROM store.vw_disposal_register" + where + " ORDER BY disposal_date DESC", p));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETURN REPORTS
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns summary — filterable by store, status and date range */
    @GetMapping("/returns/summary")
    public ResponseEntity<List<Map<String, Object>>> getReturnsSummary(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT rh.id, rh.return_no, rh.return_date, rh.status, " +
                "s.store_code, s.store_name, " +
                "COUNT(ri.id) AS item_count, SUM(ri.return_qty) AS total_qty " +
                "FROM store.return_header rh " +
                "JOIN store.store_site s ON rh.store_id = s.id " +
                "JOIN store.return_item ri ON rh.id = ri.return_id " +
                "WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (storeId != null)  { sql.append(" AND rh.store_id = :storeId"); p.addValue("storeId", storeId); }
        if (status  != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND rh.status = :status");
            p.addValue("status", status.toUpperCase());
        }
        if (from != null) { sql.append(" AND rh.return_date >= :from"); p.addValue("from", from); }
        if (to   != null) { sql.append(" AND rh.return_date <= :to");   p.addValue("to", to); }
        sql.append(" GROUP BY rh.id, rh.return_no, rh.return_date, rh.status, s.store_code, s.store_name");
        sql.append(" ORDER BY rh.return_date DESC");
        return ResponseEntity.ok(jdbc.queryForList(sql.toString(), p));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DASHBOARD SUMMARY
    // ─────────────────────────────────────────────────────────────────────────

    /** High-level KPI snapshot for the dashboard */
    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<Map<String, Object>> getDashboardSummary(
            @RequestParam(required = false) UUID storeId
    ) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String storeFilter = storeId != null ? " AND sb.store_id = :storeId" : "";
        if (storeId != null) p.addValue("storeId", storeId);

        long totalItems   = queryScalar("SELECT COUNT(DISTINCT item_id) FROM store.stock_balance WHERE on_hand_qty > 0" + storeFilter.replace("sb.", ""), p);
        long lowStockItems= queryScalar("SELECT COUNT(*) FROM store.vw_low_stock" + (storeId != null ? " WHERE store_id = :storeId" : ""), p);
        long assetsIssued = queryScalar("SELECT COUNT(*) FROM store.asset WHERE asset_status = 'ISSUED'", new MapSqlParameterSource());
        long pendingReturns = queryScalar("SELECT COUNT(*) FROM store.return_header WHERE status IN ('SUBMITTED','RECEIVED')" + (storeId != null ? " AND store_id = :storeId" : ""), p);
        long pendingGrns  = queryScalar("SELECT COUNT(*) FROM store.grn WHERE status IN ('DRAFT','SUBMITTED','UNDER_INSPECTION')" + (storeId != null ? " AND store_id = :storeId" : ""), p);

        return ResponseEntity.ok(Map.of(
                "totalStockItems",   totalItems,
                "lowStockItems",     lowStockItems,
                "assetsIssued",      assetsIssued,
                "pendingReturns",    pendingReturns,
                "pendingGrns",       pendingGrns
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private long countView(String view, StringBuilder where, MapSqlParameterSource p) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + view + where, p, Long.class);
        return count != null ? count : 0L;
    }

    private long queryScalar(String sql, MapSqlParameterSource p) {
        Long val = jdbc.queryForObject(sql, p, Long.class);
        return val != null ? val : 0L;
    }
}
