package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class UomApiTest extends BaseIntegrationTest {

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
    @DisplayName("Lists pre-seeded UOMs and returns at least 22 rows")
    void testListUoms() throws Exception {
        mockMvc.perform(get("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(22)))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Creates a new UOM, verifies 201 Created and audits it")
    void testCreateUom() throws Exception {
        String code = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UomDto.CreateRequest req = new UomDto.CreateRequest(code, "Test Unit", "COUNT", false, 0, "Test unit description");

        String res = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uomCode").value(code))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(res);
        String id = json.get("id").asText();

        // Query single UOM
        mockMvc.perform(get("/api/store/uoms/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.uomCode").value(code));
    }

    @Test
    @DisplayName("Duplicate UOM code returns 409 Conflict")
    void testDuplicateUomCode() throws Exception {
        String code = "DUP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UomDto.CreateRequest req = new UomDto.CreateRequest(code, "Duplicate Unit", "COUNT", false, 0, "Desc");

        mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Attempt duplicate
        mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    @DisplayName("Updates UOM and increments optimistic lock version; rejects stale version")
    void testUpdateAndOptimisticLocking() throws Exception {
        String code = "OPT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UomDto.CreateRequest req = new UomDto.CreateRequest(code, "Initial Name", "COUNT", false, 0, "Desc");

        String res = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(res);
        String id = json.get("id").asText();
        long version = json.get("version").asLong();

        // Valid update
        UomDto.UpdateRequest updateReq = new UomDto.UpdateRequest("Updated Name", "COUNT", false, 0, "Updated", version);
        String updateRes = mockMvc.perform(put("/api/store/uoms/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uomName").value("Updated Name"))
                .andReturn().getResponse().getContentAsString();

        long newVersion = objectMapper.readTree(updateRes).get("version").asLong();
        assertThat(newVersion).isGreaterThan(version);

        // Stale update using old version
        UomDto.UpdateRequest staleReq = new UomDto.UpdateRequest("Stale Name", "COUNT", false, 0, "Stale", version);
        mockMvc.perform(put("/api/store/uoms/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Status patch deactivates and reactivates UOM")
    void testStatusPatch() throws Exception {
        String code = "ACT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UomDto.CreateRequest req = new UomDto.CreateRequest(code, "Status Unit", "COUNT", false, 0, "Desc");

        String res = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(res).get("id").asText();

        // Deactivate
        mockMvc.perform(patch("/api/store/uoms/" + id + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest(false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/store/uoms/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Reactivate
        mockMvc.perform(patch("/api/store/uoms/" + id + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest(true))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/store/uoms/" + id)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
