package com.nicsi.store.inventory.api;

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
 * POST /api/store/grns/{id}/post-stock is the path the PDF documents. It was built as a plain
 * second mapping on the same controller method body as /post (GrnController.java:67), delegating
 * to the same grnPostingService.postGrn call, but no test ever exercised it.
 *
 * The equivalence claim is testable directly because the idempotency key is NOT scoped by request
 * URI or method: it is stored as {headerKey}:line-{n} in stock_transaction.idempotency_key and
 * looked up by value (GrnPostingService.java:71-74). So the same Idempotency-Key replayed against
 * /post-stock must take the same fast-path replay and return the identical body. If someone ever
 * gave the alias its own service call, its own authorisation or its own key derivation, this test
 * fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GrnPostStockAliasIntegrationTest extends BaseIntegrationTest {

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
    private String storeOperatorToken;

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID serialItemId;

    @BeforeEach
    void setUp() throws Exception {
        truncateInventoryTables(jdbcTemplate, "GrnPostStockAliasIntegrationTest");

        adminToken = getToken("admin");
        storeManagerToken = getToken("store.manager");
        storeOperatorToken = getToken("store.operator");

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
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }

    /**
     * Builds an ACCEPTED GRN with one serialised line. store.manager creates and receives it so
     * the maker-checker guard is reachable; admin is the neutral poster.
     */
    private Fixture createAcceptedGrn(String tag) throws Exception {
        String poNumber = "PO-ALIAS-" + tag + "-" + System.currentTimeMillis();
        PurchaseOrderDto.CreateRequest poReq = new PurchaseOrderDto.CreateRequest(
                "NICSI_ERP", null, poNumber, LocalDate.now(),
                "DIRECT", "GEM-2026-0001", "CONT-2026",
                UUID.randomUUID(), "VEND-DELL", "Dell Global",
                "INR", new BigDecimal("60000.00"),
                null,
                List.of(new PurchaseOrderDto.CreateItemRequest(
                        1, serialItemId, "Dell Laptop",
                        new BigDecimal("2.000"), new BigDecimal("60000.00"),
                        BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null))
        );
        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID poId = UUID.fromString(objectMapper.readTree(poResp).get("id").asText());
        UUID poItemId = UUID.fromString(objectMapper.readTree(poResp).get("items").get(0).get("id").asText());

        GrnDto.CreateRequest grnReq = new GrnDto.CreateRequest(
                LocalDate.now(), testStoreId, poId,
                UUID.randomUUID(), "Dell Global",
                "INV-ALIAS", LocalDate.now(), "CH-ALIAS", LocalDate.now(),
                "Alias equivalence fixture " + tag,
                List.of(new GrnDto.CreateItemRequest(
                        poItemId, serialItemId, new BigDecimal("2.000"), new BigDecimal("60000.00"),
                        testLocationId, null, null, null, "Laptops received"))
        );
        String grnResp = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID grnId = UUID.fromString(objectMapper.readTree(grnResp).get("id").asText());
        UUID grnItemId = UUID.fromString(objectMapper.readTree(grnResp).get("items").get(0).get("id").asText());

        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk());

        String inspectionJson = mockMvc.perform(get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode inspectionNode = objectMapper.readTree(inspectionJson);

        InspectionDto.DecideRequest decideRequest = new InspectionDto.DecideRequest(
                "Passes technical inspection",
                List.of(new InspectionDto.DecideItemRequest(
                        UUID.fromString(inspectionNode.get("items").get(0).get("id").asText()),
                        new BigDecimal("2.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                        true, "GOOD", true, true,
                        "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\"}",
                        "As expected")),
                inspectionNode.get("version").asLong()
        );
        mockMvc.perform(post("/api/store/inspections/" + inspectionNode.get("id").asText() + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decideRequest)))
                .andExpect(status().isOk());

        return new Fixture(grnId, grnItemId);
    }

    private record Fixture(UUID grnId, UUID grnItemId) {
    }

    @Test
    @DisplayName("post-stock returns the identical response to /post when replayed with the same key")
    void postStockReplaysIdenticallyToPost() throws Exception {
        Fixture fixture = createAcceptedGrn("EQ");
        String postBody = objectMapper.writeValueAsString(new GrnPostDto.PostRequest(
                List.of(new GrnPostDto.LineSerialRequest(fixture.grnItemId(), List.of("ALIAS-SN-001", "ALIAS-SN-002"))),
                "Received and stored"));
        String sharedKey = "IDEM-ALIAS-SHARED-" + System.currentTimeMillis();

        // First call posts through /post, which is the primary mapping.
        String viaPost = mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalPostedLines").value(1))
                .andExpect(jsonPath("$.totalAssetsCreated").value(2))
                .andReturn().getResponse().getContentAsString();

        // Same key, same body, different endpoint. The key is not URI-scoped, so this must take
        // the same fast replay path and return the same body rather than a second posting.
        String viaPostStock = mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post-stock")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(viaPostStock))
                .as("post-stock must replay the /post response for the same idempotency key")
                .isEqualTo(objectMapper.readTree(viaPost));
        assertThat(viaPostStock)
                .as("raw bodies must match too, so a serializer-level difference is caught")
                .isEqualTo(viaPost);

        // And the replay must not have written anything twice.
        assertThat(grnRepository.findById(fixture.grnId()).orElseThrow().getStatus()).isEqualTo("POSTED");
        Integer txnCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_transaction WHERE reference_type = 'GRN' AND reference_id = ?",
                Integer.class, fixture.grnId());
        assertThat(txnCount).isEqualTo(1);
        Integer assetCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.asset WHERE grn_item_id = ?", Integer.class, fixture.grnItemId());
        assertThat(assetCount).isEqualTo(2);
    }

    @Test
    @DisplayName("post-stock posts on its own when it is the first call for a key")
    void postStockPostsAsTheFirstCall() throws Exception {
        Fixture fixture = createAcceptedGrn("FIRST");
        String body = objectMapper.writeValueAsString(new GrnPostDto.PostRequest(
                List.of(new GrnPostDto.LineSerialRequest(fixture.grnItemId(), List.of("FIRST-SN-001", "FIRST-SN-002"))),
                "Posted through the alias"));

        mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post-stock")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "IDEM-ALIAS-FIRST-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalPostedLines").value(1))
                .andExpect(jsonPath("$.totalAssetsCreated").value(2));

        assertThat(grnRepository.findById(fixture.grnId()).orElseThrow().getStatus()).isEqualTo("POSTED");
    }

    @Test
    @DisplayName("post-stock enforces the same Idempotency-Key contract as /post")
    void postStockRequiresIdempotencyKey() throws Exception {
        Fixture fixture = createAcceptedGrn("NOKEY");
        String body = objectMapper.writeValueAsString(new GrnPostDto.PostRequest(
                List.of(), "no key supplied"));

        mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post-stock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("post-stock requires a token (401) and GRN_POST (403)")
    void postStockIsGuarded() throws Exception {
        Fixture fixture = createAcceptedGrn("GUARD");
        String body = objectMapper.writeValueAsString(new GrnPostDto.PostRequest(
                List.of(), "guard check"));
        String key = "IDEM-ALIAS-GUARD-" + System.currentTimeMillis();

        mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post-stock")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // store.operator holds GRN_CREATE but not GRN_POST, so @PreAuthorize rejects before the body.
        mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post-stock")
                        .header("Authorization", "Bearer " + storeOperatorToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("post-stock enforces the same maker-checker as /post")
    void postStockEnforcesMakerChecker() throws Exception {
        Fixture fixture = createAcceptedGrn("MC");
        String body = objectMapper.writeValueAsString(new GrnPostDto.PostRequest(
                List.of(), "maker-checker check"));

        // store.manager holds GRN_POST, so @PreAuthorize passes and the service-level guard fires
        // with the same MAKER_CHECKER_VIOLATION code that /post returns for the receiver.
        mockMvc.perform(post("/api/store/grns/" + fixture.grnId() + "/post-stock")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", "IDEM-ALIAS-MC-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // The guard throws before mutating, so the GRN is still postable by admin.
        assertThat(grnRepository.findById(fixture.grnId()).orElseThrow().getStatus()).isEqualTo("ACCEPTED");
    }
}
