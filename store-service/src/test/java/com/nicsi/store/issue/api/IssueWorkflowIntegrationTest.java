package com.nicsi.store.issue.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the complete Issue lifecycle (Phase 5).
 *
 * Phase 1 — GRN→Stock: Receipt stock into the store (reuses existing GRN workflow)
 * Phase 2 — Issue Workflow:
 *   create DRAFT → submit → approve → post (with asset assignment) → idempotent replay → acknowledge
 *
 * Option-A isolation: every test run gets a unique tag so no TRUNCATE is needed.
 * Assertions are scoped to the entity IDs created by each test run.
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ─── Repositories ─────────────────────────────────────────────────────────

    @Autowired private StoreSiteRepository storeSiteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private ItemCategoryRepository itemCategoryRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private UomRepository uomRepository;
    @Autowired private StockBalanceRepository stockBalanceRepository;
    @Autowired private StockTransactionRepository stockTransactionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssueHeaderRepository issueHeaderRepository;
    @Autowired private AssetAssignmentRepository assetAssignmentRepository;

    // ─── Tokens ───────────────────────────────────────────────────────────────

    private String storeOperatorToken;
    private String storeManagerToken;

    // ─── Per-run unique tag for Option-A isolation (no TRUNCATE) ─────────────

    /** Short unique tag — changes each test-class run, stable within all @Test methods in the same @BeforeEach. */
    private String tag;

    // ─── Master data IDs (set in setUp, scoped to this tag) ───────────────────

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID serialItemId;    // NON_CONSUMABLE / SERIAL (Laptop — asset required)

    @BeforeEach
    void setUp() throws Exception {
        // Unique tag per test run — guarantees isolation without TRUNCATE
        tag = "P5-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        storeOperatorToken      = getToken("store.operator");
        storeManagerToken       = getToken("store.manager");

        // ── Master: Store & Location (unique per tag) ──────────────────────────
        StoreSite store = storeSiteRepository.findByStoreCodeIgnoreCase("ISS-STORE-" + tag).orElseGet(() -> {
            StoreSite s = new StoreSite();
            s.setStoreCode("ISS-STORE-" + tag);
            s.setStoreName("Issue Test Warehouse " + tag);
            s.setStoreType("GENERAL");
            return storeSiteRepository.save(s);
        });
        testStoreId = store.getId();

        StorageLocation location = storageLocationRepository.findAll().stream()
                .filter(l -> l.getStore().getId().equals(testStoreId) && ("ISS-RACK-" + tag).equals(l.getLocationCode()))
                .findFirst().orElseGet(() -> {
                    StorageLocation l = new StorageLocation();
                    l.setStore(store);
                    l.setLocationCode("ISS-RACK-" + tag);
                    l.setLocationName("Issue Rack " + tag);
                    l.setLocationType("RACK");
                    return storageLocationRepository.save(l);
                });
        testLocationId = location.getId();

        Uom uom = uomRepository.findByUomCodeIgnoreCase("NOS").orElseThrow();

        ItemCategory itCat = itemCategoryRepository.findByCategoryCodeIgnoreCase("IT-HW-" + tag).orElseGet(() -> {
            ItemCategory c = new ItemCategory();
            c.setCategoryCode("IT-HW-" + tag);
            c.setCategoryName("IT Hardware " + tag);
            return itemCategoryRepository.save(c);
        });

        // Serialised / asset item (Laptop) — unique code per tag
        Item serialItem = itemRepository.findByItemCodeIgnoreCase("LAPTOP-" + tag).orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("LAPTOP-" + tag);
            it.setItemName("Issue Test Laptop " + tag);
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
    private List<UUID> receiveStockAndGetAssetIds(String localTag) throws Exception {
        // Create PO — unique PO number per call to avoid UNIQUE(po_ref_id, po_line_no)
        String poNumber = "ISS-PO-" + localTag + "-" + System.nanoTime();
        PurchaseOrderDto.CreateRequest poReq = new PurchaseOrderDto.CreateRequest(
                "NICSI_ERP", null, poNumber, LocalDate.now(),
                "DIRECT", "GEM-ISS-" + localTag, "CONT-ISS-" + localTag,
                UUID.randomUUID(), "VEND-" + localTag, "Issue Test Vendor " + localTag,
                "INR", new BigDecimal("140000.00"),
                null, // rawSnapshot
                List.of(new PurchaseOrderDto.CreateItemRequest(
                        1, serialItemId, "Issue Test Laptop " + tag, new BigDecimal("2.000"),
                        new BigDecimal("70000.00"), BigDecimal.ZERO, LocalDate.now().plusDays(30),
                        null, null, null))
        );
        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID poId     = UUID.fromString(objectMapper.readTree(poResp).get("id").asText());
        UUID poItemId = UUID.fromString(objectMapper.readTree(poResp).get("items").get(0).get("id").asText());

        // Unique serial numbers per call
        String sn1 = "ISS-SN-" + localTag + "-A";
        String sn2 = "ISS-SN-" + localTag + "-B";

        // Create GRN
        GrnDto.CreateRequest grnReq = new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId,
                UUID.randomUUID(), "Issue Test Vendor " + localTag,
                "ISS-INV-" + localTag, LocalDate.now(), "ISS-CH-" + localTag, LocalDate.now(),
                "Issue test receipt " + localTag,
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
        UUID grnId     = UUID.fromString(objectMapper.readTree(grnResp).get("id").asText());
        UUID grnItemId = UUID.fromString(objectMapper.readTree(grnResp).get("items").get(0).get("id").asText());

        // Submit GRN (store.operator)
        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeOperatorToken))
                .andExpect(status().isOk());

        // Inspection decision (technical.inspector / store.manager)
        String inspResp = mockMvc.perform(MockMvcRequestBuilders.get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andReturn().getResponse().getContentAsString();
        var inspJson = objectMapper.readTree(inspResp);
        UUID inspId = UUID.fromString(inspJson.get("id").asText());
        UUID inspItemId = UUID.fromString(inspJson.get("items").get(0).get("id").asText());
        long inspVersion = inspJson.get("version").asLong();

        mockMvc.perform(post("/api/store/inspections/" + inspId + "/decide")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InspectionDto.DecideRequest(
                                "All pass",
                                List.of(new InspectionDto.DecideItemRequest(
                                        inspItemId,
                                        new BigDecimal("2.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                                        true, "GOOD", true, true,
                                        "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\"}",
                                        "Good condition")),
                                inspVersion
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Post GRN (store.manager — different from store.operator who created it, satisfying maker-checker)
        GrnPostDto.PostRequest postReq = new GrnPostDto.PostRequest(
                List.of(new GrnPostDto.LineSerialRequest(grnItemId, List.of(sn1, sn2))),
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
    // MAIN TEST: Complete lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Phase 5: Complete Issue Workflow — Receipt→Create→Submit→Approve→Post (Maker-Checker+Idempotency)→Acknowledge")
    void testCompleteIssueWorkflow() throws Exception {

        // ── Phase 1: Receipt stock ─────────────────────────────────────────────
        List<UUID> assetIds = receiveStockAndGetAssetIds(tag + "-WF");
        assertThat(assetIds).hasSize(2);

        // Verify stock balance pre-issue: 2 on hand (scoped to this item + store)
        List<StockBalance> preBal = stockBalanceRepository.findByStoreIdAndItemId(testStoreId, serialItemId);
        assertThat(preBal).hasSize(1);
        assertThat(preBal.get(0).getOnHandQty()).isEqualByComparingTo("2.000");
        assertThat(preBal.get(0).getAvgUnitCost()).isEqualByComparingTo("70000.0000");

        // ── Phase 2: Create Issue DRAFT (store.operator — will be maker) ───────
        UUID employeeId = UUID.nameUUIDFromBytes(("employee-" + tag).getBytes());
        IssueDto.CreateRequest createReq = new IssueDto.CreateRequest(
                LocalDate.now(),
                testStoreId,
                null,                           // no requisition
                "EMPLOYEE",
                employeeId,
                "John Employee " + tag,
                UUID.nameUUIDFromBytes(("dept-it-" + tag).getBytes()),
                "IT Department " + tag,
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
                .andExpect(jsonPath("$.issuedToNameSnapshot").value("John Employee " + tag))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID issueId     = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());
        UUID issueItemId = UUID.fromString(objectMapper.readTree(createResp).get("items").get(0).get("id").asText());

        // ── Phase 3: Submit (store.operator) ───────────────────────────────────
        mockMvc.perform(post("/api/store/issues/" + issueId + "/submit")
                        .header("Authorization", "Bearer " + storeOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // ── Phase 4: Approve (store.manager — ISSUE_APPROVE) ──────────────────
        mockMvc.perform(post("/api/store/issues/" + issueId + "/approve")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // ── Phase 5: Maker-Checker Guard — store.operator tries to POST → 403 ─
        // The same operator who created the issue is blocked from posting it.
        String idemKey = "ISS-POST-" + issueId + "-" + tag;
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

        // Issue is POSTED in DB
        IssueHeader issuedHeader = issueHeaderRepository.findById(issueId).orElseThrow();
        assertThat(issuedHeader.getStatus()).isEqualTo("POSTED");

        // ISSUE transaction appended to immutable ledger
        List<StockTransaction> txns = stockTransactionRepository
                .findByReferenceTypeAndReferenceId("ISSUE", issueId);
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getTransactionType()).isEqualTo("ISSUE");
        assertThat(txns.get(0).getQuantityOut()).isEqualByComparingTo("2.000");

        // Stock balance reduced: 2 → 0
        List<StockBalance> postBal = stockBalanceRepository.findByStoreIdAndItemId(testStoreId, serialItemId);
        assertThat(postBal).hasSize(1);
        assertThat(postBal.get(0).getOnHandQty()).isEqualByComparingTo("0.000");

        // Assets transitioned to ISSUED with correct custodian
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
        // Replaying with the same key must return identical fields WITHOUT modifying DB
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

        // No duplicate rows after replay
        assertThat(stockTransactionRepository.findByReferenceTypeAndReferenceId("ISSUE", issueId)).hasSize(1);
        assertThat(assetAssignmentRepository.findByIssueId(issueId)).hasSize(2);
        assertThat(postBal.get(0).getOnHandQty()).isEqualByComparingTo("0.000");

        // ── Phase 9: Acknowledge ───────────────────────────────────────────────
        // Recorded by the store officer on behalf of the recipient (store.manager has ISSUE_CREATE)
        mockMvc.perform(post("/api/store/issues/" + issueId + "/acknowledge")
                        .header("Authorization", "Bearer " + storeManagerToken)
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

    // ─────────────────────────────────────────────────────────────────────────
    // GUARD TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Create Issue — missing storeId returns 400")
    void testCreateIssueMissingStoreId() throws Exception {
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
    @DisplayName("Double-submit returns 400 INVALID_STATE")
    void testDoubleSubmitRejected() throws Exception {
        // Receive stock first (unique sub-tag so serial numbers don't collide)
        receiveStockAndGetAssetIds(tag + "-DS");

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
        receiveStockAndGetAssetIds(tag + "-NK");

        IssueDto.CreateRequest createReq = new IssueDto.CreateRequest(
                LocalDate.now(), testStoreId, null, "EMPLOYEE",
                UUID.randomUUID(), "Jane", null, null, null, null, "Test",
                List.of(new IssueDto.CreateItemRequest(1, serialItemId, null, null, testLocationId, null, new BigDecimal("1"), null))
        );
        // store.manager creates — operator will be the checker (different from creator)
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
                        .header("Authorization", "Bearer " + storeOperatorToken) // different user — passes maker-checker
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
