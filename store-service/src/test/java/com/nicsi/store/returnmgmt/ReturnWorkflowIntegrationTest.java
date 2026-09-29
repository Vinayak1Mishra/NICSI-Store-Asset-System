package com.nicsi.store.returnmgmt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.master.domain.*;
import com.nicsi.store.master.repository.*;
import com.nicsi.store.returnmgmt.domain.ReturnHeader;
import com.nicsi.store.returnmgmt.dto.ReturnDto;
import com.nicsi.store.returnmgmt.repository.ReturnHeaderRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class ReturnWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreSiteRepository storeSiteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private ItemCategoryRepository categoryRepository;

    @Autowired
    private UomRepository uomRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ReturnHeaderRepository returnHeaderRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    private StoreSite store;
    private StorageLocation location;
    private Item item;
    private String tag;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);

        Uom uom = new Uom();
        uom.setUomCode("UOM-" + tag);
        uom.setUomName("Unit " + tag);
        uom.setUomType("COUNT");
        uom.setCreatedBy(UUID.randomUUID());
        uom = uomRepository.save(uom);

        ItemCategory cat = new ItemCategory();
        cat.setCategoryCode("CAT-" + tag);
        cat.setCategoryName("Category " + tag);
        cat.setCreatedBy(UUID.randomUUID());
        cat = categoryRepository.save(cat);

        item = new Item();
        item.setItemCode("ITM-" + tag);
        item.setItemName("Test Hardware " + tag);
        item.setItemType("NON_CONSUMABLE");
        item.setTrackingType("QUANTITY");
        item.setCategory(cat);
        item.setBaseUom(uom);
        item.setCreatedBy(UUID.randomUUID());
        item = itemRepository.save(item);

        store = new StoreSite();
        store.setStoreCode("STR-" + tag);
        store.setStoreName("Test Store " + tag);
        store.setStoreType("GENERAL");
        store.setCreatedBy(UUID.randomUUID());
        store = storeSiteRepository.save(store);

        location = new StorageLocation();
        location.setStore(store);
        location.setLocationCode("LOC-" + tag);
        location.setLocationName("Rack 1 " + tag);
        location.setLocationType("RACK");
        location.setCreatedBy(UUID.randomUUID());
        location = storageLocationRepository.save(location);
    }

    private String obtainDevToken(String username) throws Exception {
        String json = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("token").asText();
    }

    @Test
    @DisplayName("Complete Return Workflow: DRAFT -> SUBMIT -> INSPECT -> POST to Inventory")
    void testCompleteReturnWorkflow() throws Exception {
        String adminToken = obtainDevToken("admin");

        // 1. Create Return Slip (DRAFT)
        ReturnDto.CreateRequest createReq = new ReturnDto.CreateRequest(
                store.getId(),
                LocalDate.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Employee Return on project completion",
                List.of(
                        new ReturnDto.LineRequest(
                                item.getId(),
                                null,
                                new BigDecimal("5.000"),
                                location.getId(),
                                "GOOD",
                                "All 5 units returned in good condition"
                        )
                )
        );

        String createJson = mockMvc.perform(post("/api/store/returns")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnNo").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(createJson);
        UUID returnId = UUID.fromString(root.path("id").asText());
        UUID lineId = UUID.fromString(root.path("items").get(0).path("id").asText());

        // 2. Submit Return Slip
        mockMvc.perform(post("/api/store/returns/" + returnId + "/submit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // 3. Inspect Return Slip (Receive & Inspect)
        ReturnDto.ReceiveRequest receiveReq = new ReturnDto.ReceiveRequest(
                UUID.randomUUID(),
                List.of(
                        new ReturnDto.ReceiveLineRequest(
                                lineId,
                                "GOOD",
                                "RESTOCK",
                                location.getId(),
                                "Verified operational by Store Tech"
                        )
                )
        );

        mockMvc.perform(post("/api/store/returns/" + returnId + "/receive")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSPECTED"))
                .andExpect(jsonPath("$.items[0].disposition").value("RESTOCK"));

        // 4. Post Return to Inventory (Updates stock balance)
        ReturnDto.PostRequest postReq = new ReturnDto.PostRequest("Store Keeper approval for restock");
        mockMvc.perform(post("/api/store/returns/" + returnId + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        // 5. Verify Database Entity State
        ReturnHeader postedHeader = returnHeaderRepository.findById(returnId).orElseThrow();
        assertThat(postedHeader.getStatus()).isEqualTo("POSTED");

        // 6. Verify Stock Balance is updated (use non-locking query — no active TX needed)
        StockBalance balance = stockBalanceRepository.findByDimensions(
                item.getId(), store.getId(), location.getId(), null
        ).orElse(null);

        assertThat(balance).isNotNull();
        assertThat(balance.getOnHandQty()).isEqualByComparingTo(new BigDecimal("5.000"));

        // 7. Test Search and Get API
        mockMvc.perform(get("/api/store/returns/" + returnId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(returnId.toString()))
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(get("/api/store/returns?storeId=" + store.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].returnNo").value(postedHeader.getReturnNo()));
    }
}
