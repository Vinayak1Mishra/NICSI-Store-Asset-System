package com.nicsi.store.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests that all error responses follow the standard ErrorResponse JSON format
 * with timestamp, status, error, code, message, correlationId, path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ErrorHandlerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String getToken(String username) throws Exception {
        String response = mockMvc.perform(post("/api/store/dev/token")
                .param("username", username))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    @Test
    void testAccessDeniedResponseFormat() throws Exception {
        String token = getToken("employee.john");

        mockMvc.perform(get("/api/store/ping")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/store/ping"));
    }

    @Test
    void test401ErrorFormat() throws Exception {
        mockMvc.perform(get("/api/store/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/store/whoami"));
    }

    @Test
    void testAccessDeniedException403() throws Exception {
        String token = getToken("employee.john");
        mockMvc.perform(get("/api/store/ping")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").exists());
    }
}
