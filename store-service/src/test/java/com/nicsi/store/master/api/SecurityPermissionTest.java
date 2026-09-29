package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityPermissionTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String employeeToken;
    private String officerToken;
    private String adminToken;

    private String getToken(String username) throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        employeeToken = getToken("employee.john");
        officerToken = getToken("store.officer");
        adminToken = getToken("admin");
    }

    @Test
    @DisplayName("Employee without ITEM_CREATE gets 403 on POST /api/store/uoms; Store Officer gets 201")
    void testItemCreatePermission() throws Exception {
        UomDto.CreateRequest req = new UomDto.CreateRequest(
                "UOM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "Test", "COUNT", false, 0, null);

        // Employee lacks ITEM_CREATE -> 403
        mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + employeeToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Officer has ITEM_CREATE -> 201
        mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + officerToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Store Officer without STORE_ADMIN gets 403 on POST /api/store/stores; Admin gets 201")
    void testStoreAdminPermission() throws Exception {
        StoreSiteDto.CreateRequest req = new StoreSiteDto.CreateRequest(
                "STR_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), "Admin Store", null, null, null, null, "GENERAL");

        // Officer lacks STORE_ADMIN -> 403
        mockMvc.perform(post("/api/store/stores")
                .header("Authorization", "Bearer " + officerToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Admin has STORE_ADMIN -> 201
        mockMvc.perform(post("/api/store/stores")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
