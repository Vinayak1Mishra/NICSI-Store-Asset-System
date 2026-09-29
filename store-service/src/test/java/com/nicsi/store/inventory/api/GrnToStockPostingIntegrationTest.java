package com.nicsi.store.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.repository.InventoryLotRepository;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GrnToStockPostingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private InventoryLotRepository inventoryLotRepository;

    @Autowired
    private GrnRepository grnRepository;

    private String storeOperatorToken;
    private String storeManagerToken;
    // "inspector" is not one of the eleven PDF 18.1 mock users, so no mock user is added.
    // store.manager creates and receives the GRN, so it must NOT be the inspector: that would
    // trip the receiver-cannot-inspect guard (InspectionService.java:124) on the happy path.
    // admin passes via STORE_ADMIN and is neither receiver nor creator, so it is the real
    // inspector and the real poster.
    private String adminToken;

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID serialItemId;
    private UUID lotItemId;

    @BeforeEach
    void setUp() throws Exception {
        // Guarded TRUNCATE replaces the old DELETE statements. DELETE could not work:
        // store.stock_transaction has the trg_stock_txn_immutable BEFORE UPDATE OR DELETE
        // row trigger, so once any run has posted to the ledger every subsequent run died
        // with "stock_transaction is immutable; DELETE not allowed". TRUNCATE bypasses
        // row-level triggers, which is safe only on nicsi_store_test -- the helper asserts
        // that before touching anything.
        truncateInventoryTables(jdbcTemplate, "GrnToStockPostingIntegrationTest");

        storeOperatorToken = getToken("store.operator");
        storeManagerToken = getToken("store.manager");
        adminToken = getToken("admin");

        StoreSite store = storeSiteRepository.findByStoreCodeIgnoreCase("MAIN-STORE").orElseGet(() -> {
            StoreSite s = new StoreSite();
            s.setStoreCode("MAIN-STORE");
            s.setStoreName("Main Warehouse");
            s.setStoreType("GENERAL");
            return storeSiteRepository.save(s);
        });
        testStoreId = store.getId();

        StorageLocation location = storageLocationRepository.findAll().stream()
                .filter(l -> l.getStore().getId().equals(testStoreId) && "RACK-01".equals(l.getLocationCode()))
                .findFirst().orElseGet(() -> {
                    StorageLocation l = new StorageLocation();
                    l.setStore(store);
                    l.setLocationCode("RACK-01");
                    l.setLocationName("Rack 01");
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

        ItemCategory medCat = itemCategoryRepository.findByCategoryCodeIgnoreCase("MED").orElseGet(() -> {
            ItemCategory c = new ItemCategory();
            c.setCategoryCode("MED");
            c.setCategoryName("Medical Supplies");
            return itemCategoryRepository.save(c);
        });

        // 1. Serialised item (Laptop)
        Item serialItem = itemRepository.findByItemCodeIgnoreCase("LAPTOP-DELL").orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("LAPTOP-DELL");
            it.setItemName("Dell Latitude 5420");
            it.setCategory(itCat);
            it.setBaseUom(uom);
            it.setItemType("NON_CONSUMABLE");
            it.setTrackingType("SERIAL");
            it.setAssetRequired(true);
            it.setStandardRate(new BigDecimal("60000.00"));
            return itemRepository.save(it);
        });
        serialItemId = serialItem.getId();

        // 2. LOT-tracked item
        Item lotItem = itemRepository.findByItemCodeIgnoreCase("FIRST-AID-KIT").orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("FIRST-AID-KIT");
            it.setItemName("First Aid Kit Deluxe");
            it.setCategory(medCat);
            it.setBaseUom(uom);
            it.setItemType("CONSUMABLE");
            it.setTrackingType("LOT");
            it.setExpiryTracking(true);
            it.setStandardRate(new BigDecimal("1500.00"));
            return itemRepository.save(it);
        });
        lotItemId = lotItem.getId();
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp);
        return node.get("token").asText();
    }

    @Test
    @DisplayName("Complete PO -> GRN -> Inspection -> Post GRN with Asset Creation, Lot Creation, Idempotency Replay & Maker-Checker")
    void testCompleteGrnPostToInventoryWorkflow() throws Exception {
        // Step 1: Create PO
        String poNumber = "PO-POST-" + System.currentTimeMillis();
        PurchaseOrderDto.CreateRequest poReq = new PurchaseOrderDto.CreateRequest(
                "NICSI_ERP", null, poNumber, LocalDate.now(),
                "DIRECT", "GEM-2026-0001", "CONT-2026",
                UUID.randomUUID(), "VEND-DELL", "Dell Global",
                "INR", new BigDecimal("123000.00"),
                null,
                List.of(
                        new PurchaseOrderDto.CreateItemRequest(1, serialItemId, "Dell Laptop", new BigDecimal("2.000"), new BigDecimal("60000.00"), BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null),
                        new PurchaseOrderDto.CreateItemRequest(2, lotItemId, "First Aid Kit", new BigDecimal("2.000"), new BigDecimal("1500.00"), BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null)
                )
        );

        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID poId = UUID.fromString(objectMapper.readTree(poResp).get("id").asText());
        UUID poSerialItemId = UUID.fromString(objectMapper.readTree(poResp).get("items").get(0).get("id").asText());
        UUID poLotItemId = UUID.fromString(objectMapper.readTree(poResp).get("items").get(1).get("id").asText());

        // Step 2: Create GRN as Store Manager
        // store.manager is the GRN creator AND the receiver (GrnService sets receivedByUserId
        // on create). It holds GRN_CREATE, so it can create the GRN. It is deliberately NOT
        // store.operator: the maker-checker rules below are only reachable by a user who holds
        // the relevant permission, and store.operator holds neither GRN_POST nor INSPECTION_APPROVE.
        GrnDto.CreateRequest grnReq = new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId,
                UUID.randomUUID(), "Dell Global",
                "INV-9988", LocalDate.now(), "CH-1122", LocalDate.now(),
                "Initial receipt for project",
                List.of(
                        new GrnDto.CreateItemRequest(poSerialItemId, serialItemId, new BigDecimal("2.000"), new BigDecimal("60000.00"), testLocationId, null, null, null, "Laptops received"),
                        new GrnDto.CreateItemRequest(poLotItemId, lotItemId, new BigDecimal("2.000"), new BigDecimal("1500.00"), testLocationId, "BATCH-2026-A", LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), "Kits received")
                )
        );

        String grnResp = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID grnId = UUID.fromString(objectMapper.readTree(grnResp).get("id").asText());
        UUID grnSerialItemId = UUID.fromString(objectMapper.readTree(grnResp).get("items").get(0).get("id").asText());

        // Submit GRN
        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_INSPECTION"));

        // Step 3: Technical Inspection Decision
        // technical_result is a jsonb column (V3 migration) and InspectionItem maps it as
        // @JdbcTypeCode(JSON), so the value must be a JSON document. The real client already
        // sends JSON.stringify({...}) -- see store-web src/app/inspection/page.tsx:214.
        // Resolve the inspection for this GRN, then record the decision on the inspection itself.
        String inspectionByGrnJson = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/store/inspections/by-grn/" + grnId)
                                .header("Authorization", "Bearer " + storeManagerToken))
                .andReturn().getResponse().getContentAsString();
        JsonNode inspectionNode = objectMapper.readTree(inspectionByGrnJson);
        String inspectionId = inspectionNode.get("id").asText();
        Long inspectionVersion = inspectionNode.get("version").asLong();

        InspectionDto.DecideRequest decideRequest = new InspectionDto.DecideRequest(
                "All items pass physical and technical inspection",
                List.of(
                        new InspectionDto.DecideItemRequest(
                                UUID.fromString(inspectionNode.get("items").get(0).get("id").asText()),
                                new BigDecimal("2.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                                true, "GOOD", true, true,
                                "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\",\"warrantyVerified\":true,\"accessoryVerified\":true}",
                                "Excellent condition"
                        ),
                        new InspectionDto.DecideItemRequest(
                                UUID.fromString(inspectionNode.get("items").get(1).get("id").asText()),
                                new BigDecimal("2.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                                true, "GOOD", true, true,
                                "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\",\"warrantyVerified\":true,\"accessoryVerified\":true}",
                                "Sealed pack"
                        )
                ),
                inspectionVersion
        );

        // MAKER-CHECKER: the receiver (store.manager) cannot inspect the GRN it received.
        // Reaches the guard because store.manager holds INSPECTION_APPROVE, so @PreAuthorize
        // passes and InspectionService.java:124 fires. The guard throws before any mutation, so
        // the inspection stays IN_PROGRESS at the same version and admin can still decide below.
        mockMvc.perform(post("/api/store/inspections/" + inspectionId + "/decide")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // Real inspection decision by admin (neither receiver nor creator).
        mockMvc.perform(post("/api/store/inspections/" + inspectionId + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Verify GRN is now ACCEPTED
        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("ACCEPTED");

        // Step 4: PERMISSION GATE - Store Operator tries to post GRN -> MUST FAIL 403
        // store.operator holds GRN_CREATE but NOT GRN_POST, so @PreAuthorize on
        // GrnController.java:57 rejects the request before the controller body runs. The
        // service-level maker-checker is never reached, so the code is ACCESS_DENIED, not
        // MAKER_CHECKER_VIOLATION.
        mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .header("Idempotency-Key", "IDEM-GRN-POST-OPERATOR-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Step 5: MAKER-CHECKER - Store Manager (the receiver) tries to post -> MUST FAIL 403
        // store.manager holds GRN_POST, so @PreAuthorize passes and
        // GrnPostingService.java:91 rejects the receiver from posting their own GRN.
        mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", "IDEM-GRN-POST-MAKER-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // Step 6: Successful Post by admin (neither receiver nor creator)
        GrnPostDto.PostRequest postRequest = new GrnPostDto.PostRequest(
                List.of(new GrnPostDto.LineSerialRequest(grnSerialItemId, List.of("DELL-SN-001", "DELL-SN-002"))),
                "Goods received and stored"
        );

        String idempotencyKey = "IDEM-GRN-POST-" + System.currentTimeMillis();
        String postResultJson = mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalPostedLines").value(2))
                .andExpect(jsonPath("$.totalAssetsCreated").value(2))
                .andReturn().getResponse().getContentAsString();

        // Verify Database State
        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("POSTED");

        // Verify Stock Transactions
        List<StockTransaction> txns = stockTransactionRepository.findByReferenceTypeAndReferenceId("GRN", grnId);
        assertThat(txns).hasSize(2);
        assertThat(txns).allMatch(t -> "RECEIPT".equals(t.getTransactionType()));

        // Verify Stock Balances
        List<StockBalance> serialBalances = stockBalanceRepository.findByStoreIdAndItemId(testStoreId, serialItemId);
        assertThat(serialBalances).hasSize(1);
        assertThat(serialBalances.get(0).getOnHandQty()).isEqualByComparingTo("2.000");
        assertThat(serialBalances.get(0).getAvgUnitCost()).isEqualByComparingTo("60000.0000");

        // Verify Lot was created for LOT-tracked item
        Optional<InventoryLot> createdLot = inventoryLotRepository.findByItemIdAndStoreIdAndLotNumber(lotItemId, testStoreId, "BATCH-2026-A");
        assertThat(createdLot).isPresent();
        assertThat(createdLot.get().getExpiryDate()).isEqualTo(LocalDate.of(2028, 1, 1));

        // Verify Assets created with QR codes
        List<Asset> createdAssets = assetRepository.findByGrnItemId(grnSerialItemId);
        assertThat(createdAssets).hasSize(2);
        assertThat(createdAssets).allMatch(a -> a.getAssetCode().startsWith("NICSI/IT-HW/"));
        assertThat(createdAssets).allMatch(a -> a.getQrCodeValue().startsWith("NICSI-AST-"));
        assertThat(createdAssets.stream().map(Asset::getSerialNumber)).containsExactlyInAnyOrder("DELL-SN-001", "DELL-SN-002");

        // Step 7: IDEMPOTENCY REPLAY TEST (Point 1 & 2)
        // Repeating the same post request with the same Idempotency-Key as the same user must
        // return the EXACT same response body. Asserted as whole-body structural equality, not
        // field by field, so any new field added to PostResponse is automatically covered -- a
        // hand-picked subset would silently stop testing a field the moment it was renamed or
        // added. JsonNode equality compares every field including nested objects and arrays
        // (arrays order-sensitively, which is correct: line order is part of the response).
        String replayResultJson = mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode originalJson = objectMapper.readTree(postResultJson);
        JsonNode replayedJson = objectMapper.readTree(replayResultJson);

        // Whole body, every field. Field names present in the replay are the same set as the
        // original, and every value matches.
        assertThat(replayedJson.fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(() -> originalJson.fieldNames());
        assertThat(replayedJson)
                .as("idempotent replay must return the identical response body, byte for byte in structure")
                .isEqualTo(originalJson);
        // Raw bodies too, so a serializer-level difference (field order, null handling) is caught.
        assertThat(replayResultJson).isEqualTo(postResultJson);

        // Assert NO duplicate rows in stock_transaction or asset
        assertThat(stockTransactionRepository.findByReferenceTypeAndReferenceId("GRN", grnId)).hasSize(2);
        assertThat(assetRepository.findByGrnItemId(grnSerialItemId)).hasSize(2);
    }
}
