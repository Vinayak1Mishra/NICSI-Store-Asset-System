package com.nicsi.store.disposal;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the disposal posting transition, which was completely untested.
 *
 * DisposalService.post() wrote asset.condition_status = "DISPOSED", but that column is
 * constrained by asset_condition_status_check in V5__fulfilment_asset_lifecycle.sql to
 * ('NEW','GOOD','WORKING','FAIR','DAMAGED','REPAIR_REQUIRED','UNSERVICEABLE','SCRAP').
 * "DISPOSED" is not among them, so every POST /api/disposals/{id}/post raised a check
 * constraint violation and rolled the transaction back with a 500. Verified before the
 * fix: create 201, submit 200, approve 200, post 500.
 *
 * The endpoint is a primary state transition with a live caller in the UI
 * (store-web/src/app/disposal/page.tsx), and the parallel asset/api disposal path
 * (AssetRegistrationService.disposeAsset) already used the valid value SCRAP for the
 * same outcome. The only reason this survived is that the existing
 * AssetLifecycleIntegrationTest disposal coverage exercises the asset/api path and
 * never touches DisposalService.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DisposalPostIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UomRepository uomRepository;
    @Autowired private ItemCategoryRepository itemCategoryRepository;
    @Autowired private StoreSiteRepository storeSiteRepository;
    @Autowired private StorageLocationRepository storageLocationRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private AssetRepository assetRepository;

    private String adminToken;
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
        String tag = "DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

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
        a.setPurchaseCost(new BigDecimal("75000.00"));
        a.setStore(testStore);
        a.setLocation(testLocation);
        a.setAssetStatus("AVAILABLE");
        a.setConditionStatus("GOOD");
        a.setQrCodeValue("QR-" + UUID.randomUUID());
        a.setCreatedBy(uuid("a"));
        return assetRepository.save(a).getId().toString();
    }

    private String createDisposal(String assetId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "disposalMethod", "AUCTION",
                "items", List.of(Map.of("assetId", assetId))));
        var r = mockMvc.perform(post("/api/disposals")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        return objectMapper.readTree(r.getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Full DRAFT->SUBMITTED->APPROVED->POSTED flow completes and marks the asset SCRAP")
    void fullDisposalFlowPostsSuccessfully() throws Exception {
        String assetId = newAsset();
        String id = createDisposal(assetId);

        mockMvc.perform(post("/api/disposals/" + id + "/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/disposals/" + id + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // This is the call that returned 500 before the fix.
        mockMvc.perform(post("/api/disposals/" + id + "/post")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        Asset reloaded = assetRepository.findById(UUID.fromString(assetId)).orElseThrow();
        assertThat(reloaded.getAssetStatus()).isEqualTo("DISPOSED");
        // SCRAP, not "DISPOSED" - the latter is not a permitted condition_status.
        assertThat(reloaded.getConditionStatus()).isEqualTo("SCRAP");
    }

    @Test
    @DisplayName("Posting is not repeatable and the disposal ends POSTED")
    void postingTwiceIsRejected() throws Exception {
        String id = createDisposal(newAsset());
        mockMvc.perform(post("/api/disposals/" + id + "/submit")
                .header("Authorization", "Bearer " + adminToken));
        mockMvc.perform(post("/api/disposals/" + id + "/approve")
                .header("Authorization", "Bearer " + adminToken));
        mockMvc.perform(post("/api/disposals/" + id + "/post")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/disposals/" + id + "/post")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
