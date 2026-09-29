package com.nicsi.store.grn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.master.domain.*;
import com.nicsi.store.master.repository.*;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GrnInspectionIntegrationTest extends BaseIntegrationTest {

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

    private String adminToken;
    private String storeOperatorToken;
    private String storeManagerToken;

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID testItemId;

    @BeforeEach
    void setUp() throws Exception {
        // These were hand-written DELETEs in a fixed order and were FK-unsafe: nothing here
        // removed store.asset, so any asset left by a previously-run test (e.g. the 2 assets
        // GrnToStockPostingIntegrationTest creates) made the grn_item delete fail with
        // "violates foreign key constraint asset_grn_item_id_fkey". The class only passed when
        // it happened to be scheduled before the inventory tests. The guarded helper truncates
        // an explicit, FK-closed, children-first table set on nicsi_store_test only, so it is
        // safe regardless of what ran before it.
        truncateInventoryTables(jdbcTemplate, "GrnInspectionIntegrationTest");

        adminToken = getToken("admin");
        storeOperatorToken = getToken("store.operator");
        storeManagerToken = getToken("store.manager");

        // Seed master prerequisites if not present
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

        ItemCategory cat = itemCategoryRepository.findByCategoryCodeIgnoreCase("IT-HW").orElseGet(() -> {
            ItemCategory c = new ItemCategory();
            c.setCategoryCode("IT-HW");
            c.setCategoryName("IT Hardware");
            return itemCategoryRepository.save(c);
        });

        Item item = itemRepository.findByItemCodeIgnoreCase("LAPTOP-DELL").orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("LAPTOP-DELL");
            it.setItemName("Dell Latitude 5420");
            it.setCategory(cat);
            it.setBaseUom(uom);
            it.setItemType("NON_CONSUMABLE");
            it.setTrackingType("SERIAL");
            it.setStandardRate(new BigDecimal("65000.00"));
            return itemRepository.save(it);
        });
        testItemId = item.getId();
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp);
        return node.get("token").asText();
    }

    @Test
    @DisplayName("Complete PO -> GRN -> Self-Inspection Blocked (Maker-Checker) -> Approved Inspection Flow")
    void testCompleteGrnAndInspectionWorkflow() throws Exception {
        // 1. Create Purchase Order Reference
        PurchaseOrderDto.CreateItemRequest poItemReq = new PurchaseOrderDto.CreateItemRequest(
                1, testItemId, "Dell Latitude 5420 Laptop", new BigDecimal("10.000"),
                new BigDecimal("65000.00"), new BigDecimal("117000.00"),
                LocalDate.now().plusMonths(1), null, "PRJ-01", "Digital NICSI"
        );

        String uniquePoNumber = "PO-NICSI-" + System.currentTimeMillis();
        PurchaseOrderDto.CreateRequest poReq = new PurchaseOrderDto.CreateRequest(
                "NICSI_ERP", null, uniquePoNumber, LocalDate.now(),
                "GEM", "GEM-2026-0098", "CONT-101",
                UUID.randomUUID(), "VEND-001", "Dell India Pvt Ltd",
                "INR", new BigDecimal("767000.00"), "{}",
                List.of(poItemReq)
        );

        String poResponseStr = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poNumber").value(uniquePoNumber))
                .andReturn().getResponse().getContentAsString();

        JsonNode poJson = objectMapper.readTree(poResponseStr);
        UUID poId = UUID.fromString(poJson.get("id").asText());
        UUID poItemId = UUID.fromString(poJson.get("items").get(0).get("id").asText());

        // 2. Create GRN by store.operator (Receiver)
        GrnDto.CreateItemRequest grnItemReq = new GrnDto.CreateItemRequest(
                poItemId, testItemId, new BigDecimal("10.000"), new BigDecimal("65000.00"),
                testLocationId, "LOT-2026-A", null, null, "Box received in sealed condition"
        );

        GrnDto.CreateRequest grnReq = new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId, UUID.randomUUID(), "Dell India Pvt Ltd",
                "INV-2026-901", LocalDate.now(), "CH-2026-441", LocalDate.now(),
                "Delivered via courier", List.of(grnItemReq)
        );

        String grnResponseStr = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grnReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.grnNo").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode grnJson = objectMapper.readTree(grnResponseStr);
        UUID grnId = UUID.fromString(grnJson.get("id").asText());
        String grnNo = grnJson.get("grnNo").asText();
        assertThat(grnNo).matches("GRN/\\d{4}-\\d{2}/\\d{6}");

        // 3. Submit GRN for Inspection
        String submitResponseStr = mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_INSPECTION"))
                .andReturn().getResponse().getContentAsString();

        // 4. Retrieve automatically generated Inspection
        String inspectionResponseStr = mockMvc.perform(get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.inspectionNo").isNotEmpty())
                .andExpect(jsonPath("$.items[0].inspectedQty").value(10.000))
                .andReturn().getResponse().getContentAsString();

        JsonNode inspectionJson = objectMapper.readTree(inspectionResponseStr);
        UUID inspectionId = UUID.fromString(inspectionJson.get("id").asText());
        UUID inspectionItemId = UUID.fromString(inspectionJson.get("items").get(0).get("id").asText());
        Long inspectionVersion = inspectionJson.get("version").asLong();

        // 5. Maker-Checker Violation Test: store.manager (receiver) attempts to inspect / approve
        InspectionDto.DecideItemRequest decideItem = new InspectionDto.DecideItemRequest(
                inspectionItemId, new BigDecimal("8.000"), new BigDecimal("2.000"), BigDecimal.ZERO,
                true, "GOOD", true, true, "{\"diagnostics\": \"passed\"}", "8 accepted, 2 damaged"
        );
        InspectionDto.DecideRequest decideReq = new InspectionDto.DecideRequest(
                "Inspection completed", List.of(decideItem), inspectionVersion
        );

        mockMvc.perform(post("/api/store/inspections/" + inspectionId + "/decide")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // 6. Independent Checker (Admin) performs inspection decision
        mockMvc.perform(post("/api/store/inspections/" + inspectionId + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].acceptedQty").value(8.000))
                .andExpect(jsonPath("$.items[0].rejectedQty").value(2.000));

        // 7. Verify GRN status and item quantities
        mockMvc.perform(get("/api/store/grns/" + grnId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].acceptedQty").value(8.000))
                .andExpect(jsonPath("$.items[0].rejectedQty").value(2.000));

        // 8. Verify PO received quantity updated
        mockMvc.perform(get("/api/store/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].receivedQty").value(8.000))
                .andExpect(jsonPath("$.items[0].remainingQty").value(2.000));

        // 9. Verify Audit Trail
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit.event WHERE entity_id IN (?, ?, ?)",
                Integer.class, poId, grnId, inspectionId
        );
        assertThat(auditCount).isNotNull();
        assertThat(auditCount).isGreaterThanOrEqualTo(4);
    }
}
