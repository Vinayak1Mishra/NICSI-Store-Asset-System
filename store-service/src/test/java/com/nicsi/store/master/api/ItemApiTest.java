package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.master.dto.ItemCategoryDto;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.dto.StatusRequest;
import com.nicsi.store.master.dto.UomDto;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ItemApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private UUID uomId;
    private UUID categoryId;

    @BeforeEach
    void setUp() throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", "admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(res).get("token").asText();

        // Ensure a test UOM
        String uomCode = "UOM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String uomRes = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UomDto.CreateRequest(uomCode, "Uom Test", "COUNT", false, 0, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        uomId = UUID.fromString(objectMapper.readTree(uomRes).get("id").asText());

        // Ensure a test Category
        String catCode = "CAT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String catRes = mockMvc.perform(post("/api/store/categories")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ItemCategoryDto.CreateRequest(catCode, "Cat Test", null, 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        categoryId = UUID.fromString(objectMapper.readTree(catRes).get("id").asText());
    }

    @Test
    @DisplayName("Creates an Item with valid relationships; validates foreign keys and in-use deactivation guard")
    void testItemLifecycleAndInUseGuard() throws Exception {
        String itemCode = "ITM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ItemDto.CreateRequest req = new ItemDto.CreateRequest(
                itemCode, "Dell Laptop 5440", categoryId, null, uomId,
                "NON_CONSUMABLE", "SERIAL", "Short desc", "Full specs",
                "Dell", "5440", "84713010", new BigDecimal("75000.00"),
                36, 12, true, true, false, true
        );

        String itemRes = mockMvc.perform(post("/api/store/items")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemCode").value(itemCode))
                .andExpect(jsonPath("$.assetRequired").value(true))
                .andExpect(jsonPath("$.trackingType").value("SERIAL"))
                .andReturn().getResponse().getContentAsString();

        String itemId = objectMapper.readTree(itemRes).get("id").asText();

        // Invalid foreign key base_uom_id returns 404
        ItemDto.CreateRequest badUomReq = new ItemDto.CreateRequest(
                "BAD_" + UUID.randomUUID().toString().substring(0, 6), "Bad UOM Item", categoryId, null, UUID.randomUUID(),
                "CONSUMABLE", "QUANTITY", null, null, null, null, null, null, null, null, false, false, false, false
        );
        mockMvc.perform(post("/api/store/items")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badUomReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UOM_NOT_FOUND"));

        // Simulate stock balance > 0 in store.stock_balance
        // First create store and location for the stock row
        UUID storeId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO store.store_site (id, store_code, store_name, store_type) VALUES (?, ?, ?, 'GENERAL')",
                storeId, "ST_" + UUID.randomUUID().toString().substring(0, 6), "Store Test");
        UUID locationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO store.storage_location (id, store_id, location_code, location_name, location_type) VALUES (?, ?, ?, ?, 'ROOM')",
                locationId, storeId, "LOC_" + UUID.randomUUID().toString().substring(0, 6), "Room A");

        jdbcTemplate.update("INSERT INTO store.stock_balance (id, item_id, store_id, location_id, on_hand_qty, avg_unit_cost) VALUES (gen_random_uuid(), ?, ?, ?, 10.000, 500.0000)",
                UUID.fromString(itemId), storeId, locationId);

        // Deactivation should now fail because stock > 0
        mockMvc.perform(patch("/api/store/items/" + itemId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest(false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_HAS_POSITIVE_STOCK"));
    }
}
