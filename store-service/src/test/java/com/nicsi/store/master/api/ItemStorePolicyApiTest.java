package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.master.dto.ItemCategoryDto;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.dto.ItemStorePolicyDto;
import com.nicsi.store.master.dto.StoreSiteDto;
import com.nicsi.store.master.dto.UomDto;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ItemStorePolicyApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private UUID itemId;
    private UUID storeId;

    @BeforeEach
    void setUp() throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", "admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(res).get("token").asText();

        // Create UOM
        String uomRes = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UomDto.CreateRequest(
                        "UOM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "Unit", "COUNT", false, 0, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID uomId = UUID.fromString(objectMapper.readTree(uomRes).get("id").asText());

        // Create Category
        String catRes = mockMvc.perform(post("/api/store/categories")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ItemCategoryDto.CreateRequest(
                        "CAT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "Category", null, 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID catId = UUID.fromString(objectMapper.readTree(catRes).get("id").asText());

        // Create Item
        String itemRes = mockMvc.perform(post("/api/store/items")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ItemDto.CreateRequest(
                        "ITM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "Test Item", catId, null, uomId,
                        "CONSUMABLE", "QUANTITY", null, null, null, null, null, null, null, null, false, false, false, false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        itemId = UUID.fromString(objectMapper.readTree(itemRes).get("id").asText());

        // Create Store
        String storeRes = mockMvc.perform(post("/api/store/stores")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StoreSiteDto.CreateRequest(
                        "STR_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "Store", null, null, null, null, "GENERAL"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        storeId = UUID.fromString(objectMapper.readTree(storeRes).get("id").asText());
    }

    @Test
    @DisplayName("Creates Policy, validates min/max, rejects duplicates")
    void testPolicyLifecycle() throws Exception {
        // Invalid min/max
        ItemStorePolicyDto.CreateRequest badReq = new ItemStorePolicyDto.CreateRequest(
                itemId, storeId, new BigDecimal("100.000"), new BigDecimal("50.000"),
                new BigDecimal("20.000"), new BigDecimal("30.000"), false, "WEIGHTED_AVG", null
        );
        mockMvc.perform(post("/api/store/item-store-policies")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MIN_MAX"));

        // Valid request
        ItemStorePolicyDto.CreateRequest req = new ItemStorePolicyDto.CreateRequest(
                itemId, storeId, new BigDecimal("10.000"), new BigDecimal("100.000"),
                new BigDecimal("20.000"), new BigDecimal("30.000"), false, "WEIGHTED_AVG", null
        );
        mockMvc.perform(post("/api/store/item-store-policies")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minStockQty").value(10.0))
                .andExpect(jsonPath("$.maxStockQty").value(100.0));

        // Duplicate policy for same item and store returns 409
        mockMvc.perform(post("/api/store/item-store-policies")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_POLICY"));
    }
}
