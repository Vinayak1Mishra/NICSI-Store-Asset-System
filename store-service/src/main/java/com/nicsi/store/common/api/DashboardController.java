package com.nicsi.store.common.api;

import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inspection.repository.InspectionRepository;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
import com.nicsi.store.issue.repository.IssueHeaderRepository;
import com.nicsi.store.license.repository.SoftwareLicenseRepository;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.ItemStorePolicyRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.repository.UomRepository;
import com.nicsi.store.procurementref.repository.PurchaseOrderRefRepository;
import com.nicsi.store.requisition.repository.RequisitionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard statistics API that aggregates live counts from all
 * repositories across Phases 1-6 for the frontend dashboard.
 */
@RestController
@RequestMapping({"/api/store/dashboard", "/api/dashboard"})
public class DashboardController {

    private final ItemRepository itemRepository;
    private final ItemCategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final StoreSiteRepository storeRepository;
    private final StorageLocationRepository locationRepository;
    private final ItemStorePolicyRepository policyRepository;
    private final RequisitionRepository requisitionRepository;
    private final PurchaseOrderRefRepository purchaseOrderRepository;
    private final GrnRepository grnRepository;
    private final InspectionRepository inspectionRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final IssueHeaderRepository issueRepository;
    private final AssetRepository assetRepository;
    private final AssetAssignmentRepository assignmentRepository;
    private final SoftwareLicenseRepository licenseRepository;

    public DashboardController(
            ItemRepository itemRepository,
            ItemCategoryRepository categoryRepository,
            UomRepository uomRepository,
            StoreSiteRepository storeRepository,
            StorageLocationRepository locationRepository,
            ItemStorePolicyRepository policyRepository,
            RequisitionRepository requisitionRepository,
            PurchaseOrderRefRepository purchaseOrderRepository,
            GrnRepository grnRepository,
            InspectionRepository inspectionRepository,
            StockBalanceRepository stockBalanceRepository,
            IssueHeaderRepository issueRepository,
            AssetRepository assetRepository,
            AssetAssignmentRepository assignmentRepository,
            SoftwareLicenseRepository licenseRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.storeRepository = storeRepository;
        this.locationRepository = locationRepository;
        this.policyRepository = policyRepository;
        this.requisitionRepository = requisitionRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.grnRepository = grnRepository;
        this.inspectionRepository = inspectionRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.issueRepository = issueRepository;
        this.assetRepository = assetRepository;
        this.assignmentRepository = assignmentRepository;
        this.licenseRepository = licenseRepository;
    }

    /**
     * Returns aggregated counts from all modules for the dashboard KPI cards.
     * Each section corresponds to a phase in the implementation roadmap.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {

        // Phase 1: Master Data
        Map<String, Object> masters = new LinkedHashMap<>();
        masters.put("totalItems", itemRepository.count());
        masters.put("totalCategories", categoryRepository.count());
        masters.put("totalUoms", uomRepository.count());
        masters.put("totalStores", storeRepository.count());
        masters.put("totalLocations", locationRepository.count());
        masters.put("totalPolicies", policyRepository.count());

        // Phase 2: Requisitions & Approvals
        Map<String, Object> requisitions = new LinkedHashMap<>();
        requisitions.put("totalRequisitions", requisitionRepository.count());

        // Phase 3: Procurement & Receipt
        Map<String, Object> procurement = new LinkedHashMap<>();
        procurement.put("totalPurchaseOrders", purchaseOrderRepository.count());
        procurement.put("totalGrns", grnRepository.count());
        procurement.put("totalInspections", inspectionRepository.count());

        // Phase 4: Inventory & Stock
        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("totalStockBalances", stockBalanceRepository.count());

        // Phase 5: Material Issues
        Map<String, Object> issues = new LinkedHashMap<>();
        issues.put("totalIssues", issueRepository.count());

        // Phase 6: Assets & Licences
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("totalAssets", assetRepository.count());
        assets.put("totalAssignments", assignmentRepository.count());
        assets.put("totalLicenses", licenseRepository.count());

        // Assemble top-level response
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("masters", masters);
        stats.put("requisitions", requisitions);
        stats.put("procurement", procurement);
        stats.put("inventory", inventory);
        stats.put("issues", issues);
        stats.put("assets", assets);

        return ResponseEntity.ok(stats);
    }
}
