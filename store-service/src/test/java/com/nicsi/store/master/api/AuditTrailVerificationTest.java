package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuditTrailVerificationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", "admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(res).get("token").asText();
    }

    @Test
    @DisplayName("Creates and updates entity; asserts immutable audit.event contains old and new value snapshots")
    void testAuditTrailRecorded() throws Exception {
        String code = "AUD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        UomDto.CreateRequest req = new UomDto.CreateRequest(code, "Audit Unit", "COUNT", false, 0, "Desc");

        String res = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(res);
        UUID uomId = UUID.fromString(json.get("id").asText());
        long version = json.get("version").asLong();

        // Verify CREATE audit event exists
        Integer createCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.event WHERE entity_type = 'UOM' AND entity_id = ? AND action = 'CREATE' AND new_value IS NOT NULL",
                Integer.class, uomId);
        assertThat(createCount).isEqualTo(1);

        // Update UOM
        UomDto.UpdateRequest updateReq = new UomDto.UpdateRequest("Audit Unit Updated", "COUNT", false, 0, "Updated", version);
        mockMvc.perform(put("/api/store/uoms/" + uomId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Verify UPDATE audit event exists with both old_value and new_value
        Integer updateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.event WHERE entity_type = 'UOM' AND entity_id = ? AND action = 'UPDATE' AND old_value IS NOT NULL AND new_value IS NOT NULL",
                Integer.class, uomId);
        assertThat(updateCount).isEqualTo(1);
    }
}
