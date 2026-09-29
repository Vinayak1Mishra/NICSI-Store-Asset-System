package com.nicsi.store.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityFilterTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Helper to get a JWT token for a mock user via the dev token endpoint.
     */
    private String getToken(String username) throws Exception {
        String response = mockMvc.perform(post("/api/store/dev/token")
                .param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("token").asText();
    }

    @Test
    void testNoToken401() throws Exception {
        mockMvc.perform(get("/api/store/whoami"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testInvalidToken401() throws Exception {
        mockMvc.perform(get("/api/store/whoami")
                .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testValidTokenReturnsUser() throws Exception {
        String token = getToken("store.operator");

        mockMvc.perform(get("/api/store/whoami")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("store.operator"));
    }

    @Test
    void testPingWithoutAdminRole403() throws Exception {
        String token = getToken("employee.john");

        mockMvc.perform(get("/api/store/ping")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPingWithAdminRole200() throws Exception {
        String token = getToken("admin");

        mockMvc.perform(get("/api/store/ping")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
