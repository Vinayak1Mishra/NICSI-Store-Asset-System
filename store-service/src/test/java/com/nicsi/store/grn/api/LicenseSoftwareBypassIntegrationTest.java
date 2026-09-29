package com.nicsi.store.grn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inspection.dto.InspectionDto;
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
import com.nicsi.store.testutil.InventoryTestFixtures;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Closes Phase 4 checklist item 8: the LICENSE / SOFTWARE bypass.
 *
 * The guard is GrnPostingService.java:116-132. When itemType is SOFTWARE or trackingType is
 * LICENSE, the line hard-continues before any posting, so it writes no stock_transaction, no
 * stock_balance and no asset, while the rest of the GRN posts normally and the header still
 * reaches POSTED. InventoryPostingService.java:68 is a second copy of the same guard one layer
 * down; this test drives the GRN-level one, since that is the branch taken here.
 *
 * Two markers are recorded, in two different places, and the distinction matters:
 *  - "DEFERRED_LICENSE_INTAKE" is the transactionNo in the RESPONSE DTO (PostedLineResponse).
 *    It is not persisted; it tells the caller the line was deliberately not posted.
 *  - "PENDING_LICENSE_ENTITLEMENT_INTAKE" is appended to grn_item.remarks in the DATABASE.
 *    It is the durable breadcrumb for Phase 6 licence intake to pick up.
 * Both are asserted, so neither can drift unnoticed.
 *
 * Isolation is Option A: no table is truncated. Each test method mints its own uniquely tagged
 * store, location, category and items, and every assertion is scoped to the item ids that method
 * created, so the class is safe to run repeatedly against a shared database and in any order.
 *
 * The normal line is deliberately serialised and asset-required. If the normal line were a plain
 * consumable it would create no assets, and "no asset row for the licence line" would pass
 * vacuously. Contrasting 2 real assets against 0 for the licence line is what makes the assertion
 * mean something.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LicenseSoftwareBypassIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GrnRepository grnRepository;

    @Autowired
    private StoreSiteRepository storeSiteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private ItemCategoryRepository itemCategoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UomRepository uomRepository;

    private String adminToken;
    private String storeManagerToken;
    private String tag;

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID normalItemId;
    private UUID licenseItemId;
    private UUID softwareItemId;
    private String normalCode;
    private String licenseCode;
    private String softwareCode;

    @BeforeEach
    void setUp() throws Exception {
        // Option A: no truncate. These tests must never wipe shared tables, so every method
        // mints its own uniquely-keyed master data and scopes its queries to the returned ids.
        tag = InventoryTestFixtures.tag();
        adminToken = getToken("admin");
        storeManagerToken = getToken("store.manager");

        StoreSite store = new StoreSite();
        store.setStoreCode("ST-LIC-" + tag);
        store.setStoreName("Licence bypass store " + tag);
        store.setStoreType("GENERAL");
        testStoreId = storeSiteRepository.save(store).getId();

        StorageLocation location = new StorageLocation();
        location.setStore(storeSiteRepository.findById(testStoreId).orElseThrow());
        location.setLocationCode("LOC-LIC-" + tag);
        location.setLocationName("Licence bypass rack " + tag);
        location.setLocationType("RACK");
        testLocationId = storageLocationRepository.save(location).getId();

        Uom uom = uomRepository.findByUomCodeIgnoreCase(InventoryTestFixtures.UOM_CODE).orElseThrow();

        ItemCategory category = new ItemCategory();
        category.setCategoryCode("CAT-LIC-" + tag);
        category.setCategoryName("Licence bypass category " + tag);
        category = itemCategoryRepository.save(category);

        // Normal line: SERIAL + assetRequired, so it really does create balances and assets.
        normalCode = "NORMAL-" + tag;
        normalItemId = createItem(normalCode, "Dell Latitude 5420", category, uom,
                "NON_CONSUMABLE", "SERIAL", true).getId();

        // Branch one of the || in GrnPostingService.java:116 -- trackingType LICENSE.
        licenseCode = "LIC-" + tag;
        licenseItemId = createItem(licenseCode, "Windows Enterprise Licence", category, uom,
                "NON_CONSUMABLE", "LICENSE", false).getId();

        // Branch two -- itemType SOFTWARE. trackingType must be one of QUANTITY/SERIAL/LOT/LICENSE
        // (V2__master_tables.sql item_tracking_type_check); there is no "NONE", so QUANTITY is the
        // untracked-by-serial value here. SOFTWARE is a legal item_type. Using QUANTITY rather than
        // LICENSE keeps this line on the SOFTWARE arm of the || alone, so the two arms are
        // exercised independently rather than the same line satisfying both.
        softwareCode = "SW-" + tag;
        softwareItemId = createItem(softwareCode, "ERP Software Suite", category, uom,
                "SOFTWARE", "QUANTITY", false).getId();
    }

    private Item createItem(String code, String name, ItemCategory cat, Uom uom,
                            String itemType, String trackingType, boolean assetRequired) {
        Item it = new Item();
        it.setItemCode(code);
        it.setItemName(name);
        it.setCategory(cat);
        it.setBaseUom(uom);
        it.setItemType(itemType);
        it.setTrackingType(trackingType);
        it.setAssetRequired(assetRequired);
        it.setStandardRate(new BigDecimal("1000.00"));
        return itemRepository.save(it);
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    private record Line(UUID poItemId, UUID itemId, String code, BigDecimal qty) {
    }

    /**
     * Runs a GRN containing the supplied lines through create -> submit -> inspect -> post and
     * returns the posting response.
     *
     * store.manager creates and therefore receives the GRN; admin is neither receiver nor creator
     * and is the one that inspects and posts. Reusing one user for both steps would trip the
     * maker-checker guard with 403 MAKER_CHECKER_VIOLATION instead of exercising the licence bypass.
     */
    private String postGrnWithLines(String tag, List<Line> lines, List<String> serials) throws Exception {
        String poNumber = "PO-LIC-" + tag + "-" + this.tag;
        // UNIQUE(po_ref_id, po_line_no) exists on purchase_order_item_ref
        // (V3__requisition_po_grn_inspection.sql), so the line number has to be the ordinal.
        //
        // GRN line_no needs no such handling: the client never sends it. GrnDto.CreateItemRequest
        // has no lineNo field, and GrnService.java:75-78 (create) and 137-140 (update) assign
        // int lineNo = 1 then item.setLineNo(lineNo++) over the request array, so
        // UNIQUE(grn_id, line_no) is satisfied server-side by construction.
        List<PurchaseOrderDto.CreateItemRequest> poItems = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            poItems.add(new PurchaseOrderDto.CreateItemRequest(
                    i + 1, l.itemId(), l.code(),
                    l.qty(), new BigDecimal("1000.00"),
                    BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null));
        }

        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PurchaseOrderDto.CreateRequest(
                                "NICSI_ERP", null, poNumber, LocalDate.now(),
                                "DIRECT", "GEM-2026-0001", "CONT-2026",
                                UUID.randomUUID(), "VEND-MISC", "Vendor",
                                "INR", new BigDecimal("100000.00"), null, poItems))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode poNode = objectMapper.readTree(poResp);
        UUID poId = UUID.fromString(poNode.get("id").asText());

        List<GrnDto.CreateItemRequest> grnItems = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            UUID poItemId = UUID.fromString(poNode.get("items").get(i).get("id").asText());
            grnItems.add(new GrnDto.CreateItemRequest(
                    poItemId, l.itemId(), l.qty(), new BigDecimal("1000.00"),
                    testLocationId, null, null, null, l.code() + " received"));
        }

        String grnResp = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId,
                UUID.randomUUID(), "Vendor",
                "INV-" + tag + "-" + this.tag, LocalDate.now(),
                "CH-" + tag + "-" + this.tag, LocalDate.now(),
                                "Licence bypass fixture " + tag, grnItems))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode grnNode = objectMapper.readTree(grnResp);
        UUID grnId = UUID.fromString(grnNode.get("id").asText());

        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk());

        String inspectionJson = mockMvc.perform(get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode inspection = objectMapper.readTree(inspectionJson);

        List<InspectionDto.DecideItemRequest> decideItems = new java.util.ArrayList<>();
        for (JsonNode inspItem : inspection.get("items")) {
            String code = lines.stream()
                    .filter(l -> l.itemId().toString().equals(inspItem.get("itemId").asText()))
                    .map(Line::code).findFirst().orElseThrow();
            BigDecimal qty = lines.stream()
                    .filter(l -> l.code().equals(code))
                    .map(Line::qty).findFirst().orElseThrow();
            decideItems.add(new InspectionDto.DecideItemRequest(
                    UUID.fromString(inspItem.get("id").asText()),
                    qty, BigDecimal.ZERO, BigDecimal.ZERO,
                    true, "GOOD", true, true,
                    "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\"}",
                    "Accepted " + code));
        }

        mockMvc.perform(post("/api/store/inspections/" + inspection.get("id").asText() + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InspectionDto.DecideRequest(
                                "All lines accepted", decideItems, inspection.get("version").asLong()))))
                .andExpect(status().isOk());

        List<GrnPostDto.LineSerialRequest> serialRequests = new java.util.ArrayList<>();
        for (JsonNode grnItem : grnNode.get("items")) {
            String itemId = grnItem.get("itemId").asText();
            if (itemId.equals(normalItemId.toString())) {
                serialRequests.add(new GrnPostDto.LineSerialRequest(
                        UUID.fromString(grnItem.get("id").asText()), serials));
            }
        }

        return mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "IDEM-LIC-" + tag + "-" + this.tag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GrnPostDto.PostRequest(
                                serialRequests, "Posted"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID grnItemIdFor(String postJson, String itemCode) throws Exception {
        for (JsonNode line : objectMapper.readTree(postJson).get("postedLines")) {
            if (itemCode.equals(line.get("itemCode").asText())) {
                return UUID.fromString(line.get("grnItemId").asText());
            }
        }
        throw new IllegalStateException("no posted line for " + itemCode);
    }

    private long countStockBalance(UUID itemId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_balance WHERE item_id = ?", Long.class, itemId);
        return c == null ? 0L : c;
    }

    private long countAssets(UUID grnItemId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.asset WHERE grn_item_id = ?", Long.class, grnItemId);
        return c == null ? 0L : c;
    }

    private long countTransactions(UUID itemId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_transaction WHERE item_id = ?", Long.class, itemId);
        return c == null ? 0L : c;
    }

    private String remarksFor(UUID grnItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT remarks FROM store.grn_item WHERE id = ?", String.class, grnItemId);
    }

    @Test
    @DisplayName("A LICENSE-tracked line is deferred while a normal line in the same GRN posts")
    void licenseLineIsDeferredWhileNormalLinePosts() throws Exception {
        String postJson = postGrnWithLines("LIC",
                List.of(
                        new Line(null, normalItemId, normalCode, new BigDecimal("2.000")),
                        new Line(null, licenseItemId, licenseCode, new BigDecimal("1.000"))),
                List.of("LIC-SN-001", "LIC-SN-002"));

        UUID grnId = UUID.fromString(objectMapper.readTree(postJson).get("grnId").asText());
        UUID licenseGrnItemId = grnItemIdFor(postJson, licenseCode);
        UUID normalGrnItemId = grnItemIdFor(postJson, normalCode);

        // The header still completes: the deferral of one line does not block the GRN.
        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("POSTED");

        // The licence line is reported as deferred and creates no assets.
        assertThat(objectMapper.readTree(postJson)
                .get("postedLines").get(1).get("transactionNo").asText())
                .isEqualTo("DEFERRED_LICENSE_INTAKE");
        assertThat(objectMapper.readTree(postJson)
                .get("postedLines").get(1).get("assetsCreated").asInt())
                .isZero();

        // Nothing physical for the licence line.
        assertThat(countStockBalance(licenseItemId)).as("licence line must not create a stock_balance").isZero();
        assertThat(countTransactions(licenseItemId)).as("licence line must not create a stock_transaction").isZero();
        assertThat(countAssets(licenseGrnItemId)).as("licence line must not create an asset").isZero();

        // The normal line posted, and produced exactly the 2 serialised assets the fixture promised.
        assertThat(countStockBalance(normalItemId)).as("normal line must create a stock_balance").isEqualTo(1);
        assertThat(countTransactions(normalItemId)).as("normal line must create a stock_transaction").isEqualTo(1);
        assertThat(countAssets(normalGrnItemId)).as("normal line must create its 2 assets").isEqualTo(2);

        String onHand = jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ?", String.class, normalItemId);
        assertThat(new BigDecimal(onHand)).isEqualByComparingTo("2.000");

        // Durable breadcrumb for Phase 6 licence intake, persisted in the database.
        assertThat(remarksFor(licenseGrnItemId))
                .as("licence line must be marked for deferred licence intake in grn_item.remarks")
                .contains("PENDING_LICENSE_ENTITLEMENT_INTAKE");
        assertThat(remarksFor(normalGrnItemId))
                .as("the normal line must not be marked as a licence intake")
                .doesNotContain("PENDING_LICENSE_ENTITLEMENT_INTAKE");
    }

    @Test
    @DisplayName("A SOFTWARE itemType line is deferred on the same terms as a LICENSE line")
    void softwareItemTypeLineIsDeferred() throws Exception {
        String postJson = postGrnWithLines("SW",
                List.of(
                        new Line(null, normalItemId, normalCode, new BigDecimal("1.000")),
                        new Line(null, softwareItemId, softwareCode, new BigDecimal("1.000"))),
                List.of("SW-SN-001"));

        UUID grnId = UUID.fromString(objectMapper.readTree(postJson).get("grnId").asText());
        UUID softwareGrnItemId = grnItemIdFor(postJson, softwareCode);

        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("POSTED");

        assertThat(objectMapper.readTree(postJson)
                .get("postedLines").get(1).get("transactionNo").asText())
                .isEqualTo("DEFERRED_LICENSE_INTAKE");

        assertThat(countStockBalance(softwareItemId)).isZero();
        assertThat(countTransactions(softwareItemId)).isZero();
        assertThat(countAssets(softwareGrnItemId)).isZero();

        assertThat(countStockBalance(normalItemId)).isEqualTo(1);
        assertThat(countAssets(grnItemIdFor(postJson, normalCode))).isEqualTo(1);

        assertThat(remarksFor(softwareGrnItemId)).contains("PENDING_LICENSE_ENTITLEMENT_INTAKE");
    }

    @Test
    @DisplayName("A GRN of only licence lines still reaches POSTED and creates no stock at all")
    void licenceOnlyGrnPostsHeaderWithNoStock() throws Exception {
        String postJson = postGrnWithLines("ONLY",
                List.of(
                        new Line(null, licenseItemId, licenseCode, new BigDecimal("5.000")),
                        new Line(null, softwareItemId, softwareCode, new BigDecimal("3.000"))),
                List.of());

        UUID grnId = UUID.fromString(objectMapper.readTree(postJson).get("grnId").asText());

        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("POSTED");
        assertThat(objectMapper.readTree(postJson).get("totalPostedLines").asInt()).isEqualTo(2);
        assertThat(objectMapper.readTree(postJson).get("totalAssetsCreated").asInt()).isZero();

        assertThat(countStockBalance(licenseItemId)).isZero();
        assertThat(countStockBalance(softwareItemId)).isZero();
        assertThat(countTransactions(licenseItemId)).isZero();
        assertThat(countTransactions(softwareItemId)).isZero();
    }
}
