package com.nicsi.store.issue.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.issue.domain.AssetAssignment;
import com.nicsi.store.issue.domain.IssueHeader;
import com.nicsi.store.issue.dto.IssueDto;
import com.nicsi.store.issue.repository.AssetAssignmentRepository;
import com.nicsi.store.issue.repository.IssueHeaderRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.repository.UomRepository;
import com.nicsi.store.procurementref.dto.PurchaseOrderDto;
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the complete Issue lifecycle:
 *
 * Phase 1 — GRN→Stock: Receipt stock into the store (reuses existing GRN workflow)
 * Phase 2 — Issue Workflow:
 *   create DRAFT → submit → approve → post (with asset assignment) → idempotent replay → acknowledge
 *
 * Tests also verify:
 *  - Maker-Checker: the operator who created the issue cannot post it (must get 403)
 *  - Idempotency: replaying POST /post with the same Idempotency-Key returns the same result
 *  - Asset state transitions: AVAILABLE → ISSUED after posting
 *  - Asset assignment record created
 *  - Stock balance reduced by issued quantity
 *  - ISSUE stock_transaction row exists in immutable ledger
 *  - Acknowledge endpoint marks the issue as ACKNOWLEDGED
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IssueWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ─── Repositories ─────────────────────────────────────────────────────────

    @Autowired private StoreSiteRepository storeSiteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private ItemCategoryRepository itemCategoryRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private UomRepository uomRepository;
    @Autowired private StockBalanceRepository stockBalanceRepository;
    @Autowired private StockTransactionRepository stockTransactionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private GrnRepository grnRepository;
    @Autowired private IssueHeaderRepository issueHeaderRepository;
    @Autowired private AssetAssignmentRepository assetAssignmentRepository;

    // ─── Tokens ───────────────────────────────────────────────────────────────

    private String storeOperatorToken;
    private String storeManagerToken;
    private String technicalInspectorToken;

    // ─── Master data IDs ──────────────────────────────────────────────────────

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID serialItemId;    // NON_CONSUMABLE / SERIAL (Laptop — asset required)

    @BeforeEach
    void setUp() throws Exception {
        // Clean up in dependency order
        jdbcTemplate.update("DELETE FROM store.asset_assignment");
        jdbcTemplate.update("DELETE FROM store.asset");
        jdbcTemplate.update("DELETE FROM store.issue_item");
        jdbcTemplate.update("DELETE FROM store.issue_header");
        jdbcTemplate.update("DELETE FROM store.stock_adjustment_item");
        jdbcTemplate.update("DELETE FROM store.stock_adjustment");
        jdbcTemplate.update("DELETE FROM store.stock_reservation");
        jdbcTemplate.update("DELETE FROM store.stock_transaction");
        jdbcTemplate.update("DELETE FROM store.stock_balance");
        jdbcTemplate.update("DELETE FROM store.inventory_lot");
        jdbcTemplate.update("DELETE FROM store.inspection_item");
        jdbcTemplate.update("DELETE FROM store.inspection");
        jdbcTemplate.update("DELETE FROM store.grn_item");
        jdbcTemplate.update("DELETE FROM store.grn");
        jdbcTemplate.update("DELETE FROM store.purchase_order_item_ref");
        jdbcTemplate.update("DELETE FROM store.purchase_order_ref");
        jdbcTemplate.update(
            "DELETE FROM store.document_sequence WHERE document_type IN ('GRN','INSPECTION','STOCK_TXN','ASSET_IT','ASSET_MED','STOCK_RES','ISSUE')"
        );

        storeOperatorToken   = getToken("store.operator");
        storeManagerToken    = getToken("store.manager");
        technicalInspectorToken = getToken("technical.inspector");

        // ── Master: Store & Location ──────────────────────────────────────────
        StoreSite store = storeSiteRepository.findByStoreCodeIgnoreCase("ISS-STORE").orElseGet(() -> {
            StoreSite s = new StoreSite();
            s.setStoreCode("ISS-STORE");
            s.setStoreName("Issue Test Warehouse");
            s.setStoreType("GENERAL");
            return storeSiteRepository.save(s);
        });
        testStoreId = store.getId();

        StorageLocation location = storageLocationRepository.findAll().stream()
                .filter(l -> l.getStore().getId().equals(testStoreId) && "ISS-RACK-01".equals(l.getLocationCode()))
                .findFirst().orElseGet(() -> {
                    StorageLocation l = new StorageLocation();
                    l.setStore(store);
                    l.setLocationCode("ISS-RACK-01");
                    l.setLocationName("Issue Rack 01");
                    l.setLocationType("RACK");
                    return storageLocationRepository.save(l);
                });
        testLocationId = location.getId();

        Uom uom = uomRepository.findByUomCodeIgnoreCase("NOS").orElseThrow();

        ItemCategory itCat = itemCategoryRepository.findByCategoryCodeIgnoreCase("IT-HW").orElseGet(() -> {
            ItemCategory c = new ItemCategory();
            c.setCategoryCode("IT-HW");
            c.setCategoryName("IT Hardware");
            return itemCategoryRepository.save(c);
        });

        // Serialised / asset item (Laptop)
        Item serialItem = itemRepository.findByItemCodeIgnoreCase("LAPTOP-ISS-TEST").orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("LAPTOP-ISS-TEST");
            it.setItemName("Issue Test Laptop");
            it.setCategory(itCat);
            it.setBaseUom(uom);
            it.setItemType("NON_CONSUMABLE");
            it.setTrackingType("SERIAL");
            it.setAssetRequired(true);
            it.setStandardRate(new BigDecimal("70000.00"));
            return itemRepository.save(it);
        });
        serialItemId = serialItem.getId();
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER: Receipt stock into store via GRN→Inspection→Post
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Performs a complete GRN→Inspection→Post flow to get 2 laptop assets into stock.
     * Returns the list of asset IDs created.
     */
    private List<UUID> receiveStockAndGetAssetIds() throws Exception {
        // Create PO
        String poNumber = "ISS-PO-" + System.currentTimeMillis();
        PurchaseOrderDto.CreateRequest poReq = new PurchaseOrderDto.CreateRequest(
                "NICSI_ERP", null, poNumber, LocalDate.now(),
                "DIRECT", "GEM-ISS-001", "CONT-ISS-001",
                UUID.randomUUID(), "VEND-ISSUE", "Issue Test Vendor",
                "INR", new BigDecimal("140000.00"),
                List.of(new PurchaseOrderDto.CreateItemRequest(
                        1, serialItemId, "Issue Test Laptop", new BigDecimal("2.000"),
                        new BigDecimal("70000.00"), BigDecimal.ZERO, LocalDate.now().plusDays(30),
                        null, null, null))
        );
        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID poId = UUID.fromString(objectMapper.readTree(poResp).get("id").asText());
        UUID poItemId = UUID.fromString(objectMapper.readTree(poResp).get("items").get(0).get("id").asText());

        // Create GRN
        GrnDto.CreateRequest grnReq = new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId,
                UUID.randomUUID(), "Issue Test Vendor",
                "ISS-INV-001", LocalDate.now(), "ISS-CH-001", LocalDate.now(),
                "Issue test receipt",
                List.of(new GrnDto.CreateItemRequest(
                        poItemId, serialItemId, new BigDecimal("2.000"),
                        new BigDecimal("70000.00"), testLocationId, null, null, null, "Laptops for issue test"))
        );
        String grnResp = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID grnId = UUID.fromString(objectMapper.readTree(grnResp).get("id").asText());
        UUID grnItemId = UUID.fromString(objectMapper.readTree(grnResp).get("items").get(0).get("id").asText());

        // Submit GRN
        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeOperatorToken))
                .andExpect(status().isOk());

        // Inspection decision
        String inspResp = mockMvc.perform(MockMvcRequestBuilders.get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andReturn().getResponse().getContentAsString();
        UUID inspItemId = UUID.fromString(objectMapper.readTree(inspResp).get("items").get(0).get("id").asText());

        mockMvc.perform(post("/api/store/inspections/by-grn/" + grnId + "/decision")
                        .header("Authorization", "Bearer " + technicalInspectorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InspectionDto.DecideRequest(
                                0L, "All pass",
                                List.of(new InspectionDto.DecideItemRequest(
                                        inspItemId,
                                        new BigDecimal("2.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                                        true, "GOOD", true, true, "PASSED", "Good condition"))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Post GRN
        GrnPostDto.PostRequest postReq = new GrnPostDto.PostRequest(
                List.of(new GrnPostDto.LineSerialRequest(grnItemId, List.of("ISS-SN-001", "ISS-SN-002"))),
                "Receipt complete"
        );
        mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", "ISS-GRN-POST-" + grnId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        // Return the created asset IDs
        return assetRepository.findByGrnItemId(grnItemId).stream()
                .map(Asset::getId).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN TEST
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Complete Issue Workflow: Receipt→Create→Submit→Approve→Post (Maker-Checker+Idempotency)→Acknowledge")
    void testCompleteIssueWorkflow() throws Exception {

        // ── Phase 1: Receipt stock ─────────────────────────────────────────────
        List<UUID> assetIds = receiveStockAndGetAssetIds();
        assertThat(assetIds).hasSize(2);

        // Verify stock balance pre-issue: 2 on hand
        List<StockBalance> preBal = stockBalanceRepository.findByStoreIdAndItemId(testStoreId, serialItemId);
        assertThat(preBal).hasSize(1);
        assertThat(preBal.get(0).getOnHandQty()).isEqualByComparingTo("2.000");
        assertThat(preBal.get(0).getAvgUnitCost()).isEqualByComparingTo("70000.0000");

        // ── Phase 2: Create Issue DRAFT (Store Operator) ───────────────────────
        UUID employeeId = UUID.nameUUIDFromBytes("employee.john".getBytes());
        IssueDto.CreateRequest createReq = new IssueDto.CreateRequest(
                LocalDate.now(),
                testStoreId,
                null,                           // no requisition
                "EMPLOYEE",
                employeeId,
                "John Employee",
                UUID.nameUUIDFromBytes("dept-it".getBytes()),
                "IT Department",
                null, null,
                "Laptop for project work",
                List.of(new IssueDto.CreateItemRequest(
                        1, serialItemId, null, null,
                        testLocationId, null,
                        new BigDecimal("2.000"),
                        "2 laptops for John"
                ))
        );

        String createResp = mockMvc.perform(post("/api/store/issues")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.issuedToType").value("EMPLOYEE"))
                .andExpect(jsonPath("$.issuedToNameSnapshot").value("John Employee"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID issueId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());
        UUID issueItemId = UUID.fromString(objectMapper.readTree(createResp).get("items").get(0).get("id").asText());

        // ── Phase 3: Submit ────────────────────────────────────────────────────
        mockMvc.perform(post("/api/store/issues/" + issueId + "/submit")
                        .header("Authorization", "Bearer " + storeOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // ── Phase 4: Approve (Store Manager) ──────────────────────────────────
        mockMvc.perform(post("/api/store/issues/" + issueId + "/approve")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // ── Phase 5: Maker-Checker Test — Operator tries to POST → 403 ─────────
        // The store.operator created the issue and must be blocked from posting it
        String idemKey = "ISS-POST-" + System.currentTimeMillis();
        IssueDto.PostRequest postRequest = new IssueDto.PostRequest(
                List.of(new IssueDto.LineAssetRequest(issueItemId, assetIds)),
                "Issue posted"
        );

        mockMvc.perform(post("/api/store/issues/" + issueId + "/post")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // ── Phase 6: Post by Manager (different user) ──────────────────────────
        String postResultJson = mockMvc.perform(post("/api/store/issues/" + issueId + "/post")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalPostedLines").value(1))
                .andExpect(jsonPath("$.totalAssetsAssigned").value(2))
                .andReturn().getResponse().getContentAsString();

        // ── Phase 7: Database verification ────────────────────────────────────

        // Issue is POSTED
        IssueHeader issuedHeader = issueHeaderRepository.findById(issueId).orElseThrow();
        assertThat(issuedHeader.getStatus()).isEqualTo("POSTED");

        // ISSUE transaction appended to immutable ledger
        List<StockTransaction> txns = stockTransactionRepository.findByReferenceTypeAndReferenceId("ISSUE", issueId);
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getTransactionType()).isEqualTo("ISSUE");
        assertThat(txns.get(0).getQuantityOut()).isEqualByComparingTo("2.000");

        // Stock balance reduced: 2 → 0
        List<StockBalance> postBal = stockBalanceRepository.findByStoreIdAndItemId(testStoreId, serialItemId);
        assertThat(postBal).hasSize(1);
        assertThat(postBal.get(0).getOnHandQty()).isEqualByComparingTo("0.000");

        // Assets transitioned to ISSUED and have correct custodian
        for (UUID assetId : assetIds) {
            Asset asset = assetRepository.findById(assetId).orElseThrow();
            assertThat(asset.getAssetStatus()).isEqualTo("ISSUED");
            assertThat(asset.getCurrentCustodianUserId()).isEqualTo(employeeId);
            assertThat(asset.getIssueItemId()).isEqualTo(issueItemId);
        }

        // Asset assignment records created (ACTIVE)
        List<AssetAssignment> assignments = assetAssignmentRepository.findByIssueId(issueId);
        assertThat(assignments).hasSize(2);
        assertThat(assignments).allMatch(a -> "ACTIVE".equals(a.getStatus()));
        assertThat(assignments).allMatch(a -> "EMPLOYEE".equals(a.getAssignmentType()));
        assertThat(assignments).allMatch(a -> employeeId.equals(a.getAssigneeUserId()));

        // ── Phase 8: Idempotency Replay ────────────────────────────────────────
        // Replaying with the same key must return the identical result WITHOUT modifying DB
        String replayJson = mockMvc.perform(post("/api/store/issues/" + issueId + "/post")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode orig   = objectMapper.readTree(postResultJson);
        JsonNode replay = objectMapper.readTree(replayJson);

        assertThat(replay.get("issueId").asText()).isEqualTo(orig.get("issueId").asText());
        assertThat(replay.get("status").asText()).isEqualTo(orig.get("status").asText());
        assertThat(replay.get("totalPostedLines").asInt()).isEqualTo(orig.get("totalPostedLines").asInt());
        assertThat(replay.get("totalAssetsAssigned").asInt()).isEqualTo(orig.get("totalAssetsAssigned").asInt());

        // No duplicate rows in ledger or assignments
        assertThat(stockTransactionRepository.findByReferenceTypeAndReferenceId("ISSUE", issueId)).hasSize(1);
        assertThat(assetAssignmentRepository.findByIssueId(issueId)).hasSize(2);
        assertThat(postBal.get(0).getOnHandQty()).isEqualByComparingTo("0.000");

        // ── Phase 9: Acknowledge ───────────────────────────────────────────────
        // Recipient (employee.john — any authenticated user representing recipient) acknowledges
        String employeeToken = getToken("employee.john");
        mockMvc.perform(post("/api/store/issues/" + issueId + "/acknowledge")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IssueDto.AcknowledgeRequest("ACCEPTED", "All items received in good condition"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgementStatus").value("ACCEPTED"));

        // Final state checks
        IssueHeader ackHeader = issueHeaderRepository.findById(issueId).orElseThrow();
        assertThat(ackHeader.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(ackHeader.getAcknowledgedAt()).isNotNull();
        assertThat(ackHeader.getAcknowledgementStatus()).isEqualTo("ACCEPTED");

        // Asset assignments should have acknowledgement timestamp
        List<AssetAssignment> ackAssignments = assetAssignmentRepository.findByIssueId(issueId);
        assertThat(ackAssignments).allMatch(a -> a.getAcknowledgementAt() != null);
    }

    @Test
    @DisplayName("Create Issue - missing storeId returns 400")
    void testCreateIssueMissingStoreId() throws Exception {
        // Attempt to create an issue without storeId (null) — should fail validation
        String payload = objectMapper.writeValueAsString(new IssueDto.CreateRequest(
                LocalDate.now(), null, null, "EMPLOYEE",
                UUID.randomUUID(), "John", null, null, null, null, null,
                List.of(new IssueDto.CreateItemRequest(1, serialItemId, null, null, testLocationId, null, new BigDecimal("1"), null))
        ));

        mockMvc.perform(post("/api/store/issues")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Submit issue that is already SUBMITTED returns 400")
    void testDoubleSubmitRejected() throws Exception {
        // Receive stock first
        receiveStockAndGetAssetIds();

        // Create + submit
        IssueDto.CreateRequest createReq = new IssueDto.CreateRequest(
                LocalDate.now(), testStoreId, null, "EMPLOYEE",
                UUID.randomUUID(), "Jane", null, null, null, null, "Test",
                List.of(new IssueDto.CreateItemRequest(1, serialItemId, null, null, testLocationId, null, new BigDecimal("1"), null))
        );
        String resp = mockMvc.perform(post("/api/store/issues")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID issueId = UUID.fromString(objectMapper.readTree(resp).get("id").asText());

        mockMvc.perform(post("/api/store/issues/" + issueId + "/submit")
                        .header("Authorization", "Bearer " + storeOperatorToken))
                .andExpect(status().isOk());

        // Second submit should fail
        mockMvc.perform(post("/api/store/issues/" + issueId + "/submit")
                        .header("Authorization", "Bearer " + storeOperatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("Post issue without Idempotency-Key header returns 400")
    void testPostIssueWithoutIdempotencyKey() throws Exception {
        // Receive stock
        receiveStockAndGetAssetIds();

        // Create + submit + approve
        IssueDto.CreateRequest createReq = new IssueDto.CreateRequest(
                LocalDate.now(), testStoreId, null, "EMPLOYEE",
                UUID.randomUUID(), "Jane", null, null, null, null, "Test",
                List.of(new IssueDto.CreateItemRequest(1, serialItemId, null, null, testLocationId, null, new BigDecimal("1"), null))
        );
        String resp = mockMvc.perform(post("/api/store/issues")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID issueId = UUID.fromString(objectMapper.readTree(resp).get("id").asText());

        mockMvc.perform(post("/api/store/issues/" + issueId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk());

        // Post without Idempotency-Key → 400
        mockMvc.perform(post("/api/store/issues/" + issueId + "/post")
                        .header("Authorization", "Bearer " + storeOperatorToken) // different user to pass maker-checker
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
