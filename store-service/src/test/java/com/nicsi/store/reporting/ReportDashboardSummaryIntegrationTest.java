package com.nicsi.store.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Executes the dashboard summary queries for real.
 *
 * getDashboardSummary builds five hand-written SQL statements. Until now the only test
 * that touched /api/reports/dashboard/summary asserted a 401 for an anonymous caller,
 * which never reaches the controller, so none of these statements had ever been run
 * against the database. getDashboardSummary consequently shipped querying
 * store.grn_header, a table that does not exist, so every authenticated call failed
 * with BadSqlGrammarException: relation "store.grn_header" does not exist. The real
 * table is store.grn (V3__requisition_po_grn_inspection.sql), and the status filter
 * used 'INSPECTED', which is absent from the column's CHECK constraint, so it could
 * never match a GRN awaiting inspection; the correct value is 'UNDER_INSPECTION'.
 *
 * These assertions run the statements and check the response shape, so a future typo
 * in a table or column name surfaces as a failing test rather than a runtime error on
 * the dashboard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReportDashboardSummaryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    private String getToken(String username) throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        adminToken = getToken("admin");
    }

    @Test
    @DisplayName("Authenticated GET /api/reports/dashboard/summary returns 200 and all five counters")
    void dashboardSummaryReturnsAllFiveCounters() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStockItems").isNumber())
                .andExpect(jsonPath("$.lowStockItems").isNumber())
                .andExpect(jsonPath("$.assetsIssued").isNumber())
                .andExpect(jsonPath("$.pendingReturns").isNumber())
                .andExpect(jsonPath("$.pendingGrns").isNumber());
    }

    @Test
    @DisplayName("storeId filter branch of the dashboard summary also executes")
    void dashboardSummaryWithStoreFilterReturns200() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard/summary")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("storeId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStockItems").isNumber())
                .andExpect(jsonPath("$.lowStockItems").isNumber())
                .andExpect(jsonPath("$.assetsIssued").isNumber())
                .andExpect(jsonPath("$.pendingReturns").isNumber())
                .andExpect(jsonPath("$.pendingGrns").isNumber());
    }
}
