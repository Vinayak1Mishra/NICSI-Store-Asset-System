package com.nicsi.store.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.testutil.BaseIntegrationTest;
import com.nicsi.store.testutil.IdempotencyTestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for Idempotency-Key header validation on @IdempotentPost endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(IdempotencyTestController.class)
class IdempotencyHeaderTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String getValidToken() throws Exception {
        String response = mockMvc.perform(post("/api/store/dev/token")
                .param("username", "admin"))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    @Test
    void testMissingIdempotencyKey400() throws Exception {
        mockMvc.perform(post("/api/store/test/idempotent-action")
                .header("Authorization", "Bearer " + getValidToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"));
    }

    @Test
    void testTooShortKey400() throws Exception {
        mockMvc.perform(post("/api/store/test/idempotent-action")
                .header("Authorization", "Bearer " + getValidToken())
                .header("Idempotency-Key", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testTooLongKey400() throws Exception {
        String longKey = "a".repeat(151);
        mockMvc.perform(post("/api/store/test/idempotent-action")
                .header("Authorization", "Bearer " + getValidToken())
                .header("Idempotency-Key", longKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testValidKey200() throws Exception {
        mockMvc.perform(post("/api/store/test/idempotent-action")
                .header("Authorization", "Bearer " + getValidToken())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void testNonIdempotentEndpointIgnored() throws Exception {
        // GET /whoami has no @IdempotentPost, so no Idempotency-Key required
        mockMvc.perform(get("/api/store/whoami")
                .header("Authorization", "Bearer " + getValidToken()))
                .andExpect(status().isOk());
    }
}
