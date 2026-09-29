package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.master.dto.StorageLocationDto;
import com.nicsi.store.master.dto.StoreSiteDto;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StoreSiteAndLocationApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", "admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(res).get("token").asText();
    }

    @Test
    @DisplayName("Creates StoreSite and multi-tier StorageLocations; verifies cross-store guard and tree endpoint")
    void testStoreAndLocationHierarchy() throws Exception {
        // Create Store 1
        String storeCode1 = "STR_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String storeRes1 = mockMvc.perform(post("/api/store/stores")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StoreSiteDto.CreateRequest(
                        storeCode1, "Main IT Store", null, null, null, "Building A", "IT"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID storeId1 = UUID.fromString(objectMapper.readTree(storeRes1).get("id").asText());

        // Create Store 2
        String storeCode2 = "STR_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String storeRes2 = mockMvc.perform(post("/api/store/stores")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StoreSiteDto.CreateRequest(
                        storeCode2, "General Store", null, null, null, "Building B", "GENERAL"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID storeId2 = UUID.fromString(objectMapper.readTree(storeRes2).get("id").asText());

        // Create Root Location in Store 1 (Room)
        String roomCode = "RM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String roomRes = mockMvc.perform(post("/api/store/locations")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StorageLocationDto.CreateRequest(
                        storeId1, null, roomCode, "Server Room", "ROOM", "BAR_RM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomId = UUID.fromString(objectMapper.readTree(roomRes).get("id").asText());

        // Create Child Location in Store 1 (Rack under Room)
        String rackCode = "RCK_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String rackRes = mockMvc.perform(post("/api/store/locations")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StorageLocationDto.CreateRequest(
                        storeId1, roomId, rackCode, "Rack 01", "RACK", "BAR_RCK"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID rackId = UUID.fromString(objectMapper.readTree(rackRes).get("id").asText());

        // Attempt to create location in Store 2 with parent in Store 1 (Cross-store)
        String badCode = "BAD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mockMvc.perform(post("/api/store/locations")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StorageLocationDto.CreateRequest(
                        storeId2, roomId, badCode, "Cross Store Shelf", "SHELF", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CROSS_STORE_PARENT"));

        // Fetch hierarchical tree for Store 1
        String treeRes = mockMvc.perform(get("/api/store/locations/tree")
                .param("storeId", storeId1.toString())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode treeJson = objectMapper.readTree(treeRes);
        assertThat(treeJson.isArray()).isTrue();
        assertThat(treeJson.size()).isGreaterThanOrEqualTo(1);

        // Verify root node contains the child rack
        boolean foundRoom = false;
        for (JsonNode node : treeJson) {
            if (node.get("id").asText().equals(roomId.toString())) {
                foundRoom = true;
                JsonNode children = node.get("children");
                assertThat(children.size()).isGreaterThanOrEqualTo(1);
                assertThat(children.get(0).get("id").asText()).isEqualTo(rackId.toString());
            }
        }
        assertThat(foundRoom).isTrue();
    }
}
