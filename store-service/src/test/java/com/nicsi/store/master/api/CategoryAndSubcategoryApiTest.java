package com.nicsi.store.master.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.master.dto.ItemCategoryDto;
import com.nicsi.store.master.dto.ItemSubcategoryDto;
import com.nicsi.store.master.dto.StatusRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CategoryAndSubcategoryApiTest extends BaseIntegrationTest {

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
    @DisplayName("Creates Category and child Subcategory; enforces uniqueness and in-use deactivation guard")
    void testCategoryAndSubcategoryLifecycle() throws Exception {
        String catCode = "CAT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ItemCategoryDto.CreateRequest catReq = new ItemCategoryDto.CreateRequest(catCode, "IT Hardware", "Hardware Category", 1);

        String catRes = mockMvc.perform(post("/api/store/categories")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(catReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryCode").value(catCode))
                .andReturn().getResponse().getContentAsString();

        String catId = objectMapper.readTree(catRes).get("id").asText();

        // Create Subcategory under Category
        String subCode = "SUB_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ItemSubcategoryDto.CreateRequest subReq = new ItemSubcategoryDto.CreateRequest(subCode, "Laptops", "Laptops Subcategory", 1);

        mockMvc.perform(post("/api/store/categories/" + catId + "/subcategories")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subcategoryCode").value(subCode))
                .andExpect(jsonPath("$.categoryId").value(catId));

        // Duplicate Subcategory code in SAME category returns 409
        mockMvc.perform(post("/api/store/categories/" + catId + "/subcategories")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));

        // Deactivating Category while Subcategory is active must fail with CATEGORY_IN_USE
        mockMvc.perform(patch("/api/store/categories/" + catId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest(false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }
}
