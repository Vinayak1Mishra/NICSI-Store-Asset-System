package com.nicsi.store.condemnation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.asset.domain.Asset;
import com.nicsi.store.asset.repository.AssetRepository;
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
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers three defects in the Condemnation module, all of which shipped because
 * /api/condemnations had no test coverage at all.
 *
 * 1. assessedCondition was an unvalidated String copied verbatim into
 *    store.asset.condition_status by approve(). Because that column is constrained by
 *    asset_condition_status_check in V5__fulfilment_asset_lifecycle.sql, an arbitrary
 *    value was accepted with a 201 on create and then failed at approve() with a 500
 *    check-constraint violation, long after the request that caused it succeeded.
 *    Same defect shape as the reporting store.grn_header bug fixed in bfb6603.
 *
 * 2. approve() performed no maker-checker separation, so the proposer could approve
 *    their own condemnation. The response echoed the same UUID in createdBy and
 *    approvedBy. MakerCheckerOperation.CONDEMNATION existed but was never referenced.
 *
 * 3. recommendedMethod had the same unvalidated-write problem against the
 *    condemnation_item.recommended_method CHECK.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CondemnationValidationIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UomRepository uomRepository;
    @Autowired private ItemCategoryRepository itemCategoryRepository;
    @Autowired private StoreSiteRepository storeSiteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private AssetRepository assetRepository;

    private String adminToken;
    private String auditorToken;
    private Item testItem;
    private StoreSite testStore;
    private StorageLocation testLocation;

    private String token(String username) throws Exception {
        String r = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("token").asText();
    }

    private UUID uuid(String n) {
        return UUID.nameUUIDFromBytes(n.getBytes());
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = token("admin");
        auditorToken = token("competent.authority");
        String tag = "CND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Uom uom = new Uom();
        uom.setUomCode("EA-" + tag);
        uom.setUomName("Each " + tag);
        uom.setUomType("COUNT");
        uom.setCreatedBy(uuid("u"));
        uom = uomRepository.save(uom);

        ItemCategory cat = new ItemCategory();
        cat.setCategoryCode("IT-" + tag);
        cat.setCategoryName("IT " + tag);
        cat.setCreatedBy(uuid("c"));
        cat = itemCategoryRepository.save(cat);

        testStore = new StoreSite();
        testStore.setStoreCode("STR-" + tag);
        testStore.setStoreName("HQ " + tag);
        testStore.setStoreType("IT");
        testStore.setCreatedBy(uuid("s"));
        testStore = storeSiteRepository.save(testStore);

        testLocation = new StorageLocation();
        testLocation.setStore(testStore);
        testLocation.setLocationCode("BAY-" + tag);
        testLocation.setLocationName("Bay " + tag);
        testLocation.setLocationType("SHELF");
        testLocation.setCreatedBy(uuid("l"));
        testLocation = storageLocationRepository.save(testLocation);

        testItem = new Item();
        testItem.setItemCode("SRV-" + tag);
        testItem.setItemName("Server " + tag);
        testItem.setItemType("NON_CONSUMABLE");
        testItem.setTrackingType("SERIAL");
        testItem.setCategory(cat);
        testItem.setBaseUom(uom);
        testItem.setCreatedBy(uuid("i"));
        testItem = itemRepository.save(testItem);
    }

    private String newAsset() {
        Asset a = new Asset();
        a.setAssetCode("AST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        a.setItem(testItem);
        a.setSerialNumber("SN-" + UUID.randomUUID());
        a.setPurchaseDate(LocalDate.now());
        a.setPurchaseCost(new BigDecimal("5000.00"));
        a.setStore(testStore);
        a.setLocation(testLocation);
        a.setAssetStatus("AVAILABLE");
        a.setConditionStatus("GOOD");
        a.setQrCodeValue("QR-" + UUID.randomUUID());
        a.setCreatedBy(uuid("a"));
        return assetRepository.save(a).getId().toString();
    }

    private String createBody(String assetId, String condition, String method) {
        Map<String, Object> line = new HashMap<>();
        line.put("assetId", assetId);
        if (condition != null) line.put("assessedCondition", condition);
        if (method != null) line.put("recommendedMethod", method);
        return json(Map.of("technicalReason", "Beyond economical repair", "items", List.of(line)));
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String create(String assetId, String condition, String method) throws Exception {
        var r = mockMvc.perform(post("/api/condemnations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(createBody(assetId, condition, method)))
                .andReturn().getResponse();
        return objectMapper.readTree(r.getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Invalid assessedCondition is rejected with 400 at create, not 500 at approve")
    void invalidAssessedConditionRejectedAtCreate() throws Exception {
        mockMvc.perform(post("/api/condemnations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(createBody(newAsset(), "TOTALLY_BROKEN", "SCRAP")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Every valid condition_status value is accepted")
    void allValidAssessedConditionsAccepted() throws Exception {
        for (String ok : new String[]{"NEW", "GOOD", "WORKING", "FAIR", "DAMAGED",
                                      "REPAIR_REQUIRED", "UNSERVICEABLE", "SCRAP"}) {
            mockMvc.perform(post("/api/condemnations")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content(createBody(newAsset(), ok, "SCRAP")))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("Invalid recommendedMethod is rejected with 400 at create")
    void invalidRecommendedMethodRejectedAtCreate() throws Exception {
        mockMvc.perform(post("/api/condemnations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(createBody(newAsset(), "DAMAGED", "GARBAGE_METHOD")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Proposer cannot approve their own condemnation (403)")
    void proposerCannotApproveOwnCondemnation() throws Exception {
        String id = create(newAsset(), "DAMAGED", "SCRAP");
        mockMvc.perform(post("/api/condemnations/" + id + "/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/condemnations/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Proposer cannot technically recommend their own condemnation (403)")
    void proposerCannotRecommendOwnCondemnation() throws Exception {
        String id = create(newAsset(), "DAMAGED", "SCRAP");
        mockMvc.perform(post("/api/condemnations/" + id + "/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/condemnations/" + id + "/recommend")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A different approver succeeds and the asset becomes CONDEMNED")
    void differentApproverSucceeds() throws Exception {
        String assetId = newAsset();
        String id = create(assetId, "DAMAGED", "SCRAP");
        mockMvc.perform(post("/api/condemnations/" + id + "/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/condemnations/" + id + "/recommend")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/condemnations/" + id + "/approve")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Asset reloaded = assetRepository.findById(UUID.fromString(assetId)).orElseThrow();
        assertThat(reloaded.getAssetStatus()).isEqualTo("CONDEMNED");
        assertThat(reloaded.getConditionStatus()).isEqualTo("DAMAGED");
    }
}
