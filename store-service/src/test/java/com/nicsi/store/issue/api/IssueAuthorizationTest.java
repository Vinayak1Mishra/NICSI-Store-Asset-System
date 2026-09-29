package com.nicsi.store.issue.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins down that IssueController actually enforces authorization.
 *
 * IssueController is the only controller in the codebase that was annotated with
 * @Secured rather than @PreAuthorize. SecurityConfig declares only
 * @EnableMethodSecurity(prePostEnabled = true); the securedEnabled flag defaults to
 * false, so none of those @Secured annotations were ever evaluated. The effect was that
 * the ISSUE_CREATE / ISSUE_APPROVE / ISSUE_POST checks were dead code and any
 * authenticated user could list, approve and post issues.
 *
 * procurement.officer is used as the negative subject because MockUsers grants it
 * STORE_DASHBOARD_VIEW, ITEM_VIEW, GRN_CREATE, STOCK_VIEW and REPORT_VIEW only -- no
 * ISSUE_* permission and no STORE_ADMIN. It is therefore denied by every IssueController
 * mapping, whatever the request body contains, because @PreAuthorize is evaluated on the
 * AOP proxy before argument resolution and @Valid binding.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IssueAuthorizationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String noIssuePermsToken;
    private String adminToken;

    private String getToken(String username) throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        noIssuePermsToken = getToken("procurement.officer");
        adminToken = getToken("admin");
    }

    /** No endpoint under test needs a real issue: 403 is decided before the body is read. */
    private String unknownIssueId() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("Control: admin (STORE_ADMIN) can list issues, so a 403 below is permission-driven")
    void adminCanListIssues() throws Exception {
        mockMvc.perform(get("/api/store/issues")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/store/issues without ISSUE_CREATE returns 403")
    void listIssuesForbidden() throws Exception {
        mockMvc.perform(get("/api/store/issues")
                        .header("Authorization", "Bearer " + noIssuePermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/store/issues/{id} without ISSUE_CREATE returns 403, not 404")
    void getIssueForbidden() throws Exception {
        mockMvc.perform(get("/api/store/issues/" + unknownIssueId())
                        .header("Authorization", "Bearer " + noIssuePermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/store/issues without ISSUE_CREATE returns 403")
    void createIssueForbidden() throws Exception {
        // A schema-valid body is required: Spring MVC resolves and @Valid-checks the arguments
        // before the @PreAuthorize proxy on the handler method is consulted, so an empty body
        // would be rejected with 400 before authorization is ever evaluated.
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "storeId", UUID.randomUUID().toString(),
                "issuedToType", "EMPLOYEE",
                "items", java.util.List.of(java.util.Map.of(
                        "lineNo", 1,
                        "itemId", UUID.randomUUID().toString(),
                        "locationId", UUID.randomUUID().toString(),
                        "issueQty", 1))));

        mockMvc.perform(post("/api/store/issues")
                        .header("Authorization", "Bearer " + noIssuePermsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/store/issues/{id}/submit without ISSUE_CREATE returns 403")
    void submitIssueForbidden() throws Exception {
        mockMvc.perform(post("/api/store/issues/" + unknownIssueId() + "/submit")
                        .header("Authorization", "Bearer " + noIssuePermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/store/issues/{id}/approve without ISSUE_APPROVE returns 403")
    void approveIssueForbidden() throws Exception {
        mockMvc.perform(post("/api/store/issues/" + unknownIssueId() + "/approve")
                        .header("Authorization", "Bearer " + noIssuePermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/store/issues/{id}/reject without ISSUE_APPROVE returns 403")
    void rejectIssueForbidden() throws Exception {
        mockMvc.perform(post("/api/store/issues/" + unknownIssueId() + "/reject")
                        .header("Authorization", "Bearer " + noIssuePermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/store/issues/{id}/post without ISSUE_POST returns 403")
    void postIssueForbidden() throws Exception {
        mockMvc.perform(post("/api/store/issues/" + unknownIssueId() + "/post")
                        .header("Authorization", "Bearer " + noIssuePermsToken)
                        .header("Idempotency-Key", "ISSUE-AUTHZ-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/store/issues/{id}/acknowledge without ISSUE_CREATE returns 403")
    void acknowledgeIssueForbidden() throws Exception {
        // Schema-valid body, for the same argument-resolution reason as createIssueForbidden.
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "acknowledgementStatus", "ACCEPTED",
                "remarks", "Should never be reached without ISSUE_CREATE"));

        mockMvc.perform(post("/api/store/issues/" + unknownIssueId() + "/acknowledge")
                        .header("Authorization", "Bearer " + noIssuePermsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An unauthenticated caller gets 401 on the issue list, not 403 or 404")
    void issueListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/store/issues"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
