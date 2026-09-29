package com.nicsi.store.requisition.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.master.dto.ItemCategoryDto;
import com.nicsi.store.master.dto.ItemDto;
import com.nicsi.store.master.dto.UomDto;
import com.nicsi.store.requisition.dto.RequisitionDto;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RequisitionWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String employeeToken;
    private String hodToken;
    private String storeOfficerToken;

    private UUID testItemId;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM store.requisition_item");
        jdbcTemplate.update("DELETE FROM store.requisition");
        jdbcTemplate.update("DELETE FROM workflow.action");
        jdbcTemplate.update("DELETE FROM workflow.instance");
        jdbcTemplate.update("DELETE FROM store.document_sequence WHERE document_type = 'REQUISITION'");

        adminToken = getToken("admin");
        employeeToken = getToken("employee.john");
        hodToken = getToken("hod.dept1");
        storeOfficerToken = getToken("store.officer");

        // Create test item if needed
        String uomCode = "UOM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String uomRes = mockMvc.perform(post("/api/store/uoms")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UomDto.CreateRequest(uomCode, "Workflow Item Uom", "COUNT", false, 0, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID uomId = UUID.fromString(objectMapper.readTree(uomRes).get("id").asText());

        String catCode = "CAT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String catRes = mockMvc.perform(post("/api/store/categories")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ItemCategoryDto.CreateRequest(catCode, "Workflow Cat", null, 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID catId = UUID.fromString(objectMapper.readTree(catRes).get("id").asText());

        String itemCode = "ITM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String itemRes = mockMvc.perform(post("/api/store/items")
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ItemDto.CreateRequest(
                        itemCode, "Workflow Test Item", catId, null, uomId,
                        "CONSUMABLE", "QUANTITY", "Desc", null, null, null, null,
                        BigDecimal.valueOf(150.00), null, null, false, false, false, false
                ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        testItemId = UUID.fromString(objectMapper.readTree(itemRes).get("id").asText());
    }

    private String getToken(String username) throws Exception {
        String res = mockMvc.perform(post("/api/store/dev/token").param("username", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("token").asText();
    }

    @Test
    @DisplayName("Complete Requisition Lifecycle: Create -> Submit -> MakerChecker Check -> Step 1 HOD -> Step 2 Store Officer -> Approved -> Audit Verified")
    void testCompleteRequisitionApprovalLifecycle() throws Exception {
        // 1. Employee creates a draft requisition
        RequisitionDto.CreateRequest createReq = new RequisitionDto.CreateRequest(
                null, "IT-DEPT", "IT Dept",
                null, null, null, null, null,
                "Project workstation accessories", "HIGH", LocalDate.now().plusDays(7),
                List.of(new RequisitionDto.LineRequest(testItemId, BigDecimal.valueOf(10), BigDecimal.valueOf(150.00), "Standard OEM", "Immediate need", "Logitech"))
        );

        String createRes = mockMvc.perform(post("/api/store/requisitions")
                .header("Authorization", "Bearer " + employeeToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.requisitionNo").exists())
                .andExpect(jsonPath("$.totalEstimatedAmount").value(1500.00))
                .andReturn().getResponse().getContentAsString();

        JsonNode createdJson = objectMapper.readTree(createRes);
        UUID reqId = UUID.fromString(createdJson.get("id").asText());
        String reqNo = createdJson.get("requisitionNo").asText();
        assertThat(reqNo).startsWith("REQ/");

        // 2. Submit Requisition
        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/submit")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_APPROVAL"))
                .andExpect(jsonPath("$.workflow.status").value("RUNNING"))
                .andExpect(jsonPath("$.workflow.currentStepNo").value(1));

        // 3a. Permission check: Employee without REQUISITION_APPROVE is blocked by RBAC
        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/approvals/decision")
                .header("Authorization", "Bearer " + employeeToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequisitionDto.DecisionRequest("APPROVE", "Employee approval attempt", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 3b. Maker-Checker Rule Enforcement: HOD creates a requisition, but CANNOT approve their own requisition
        RequisitionDto.CreateRequest hodCreateReq = new RequisitionDto.CreateRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "IT-DEPT", "IT Dept",
                null, null, null, null, null,
                "HOD personal workstation requisition", "NORMAL", LocalDate.now().plusDays(5),
                List.of(new RequisitionDto.LineRequest(testItemId, BigDecimal.valueOf(1), BigDecimal.valueOf(150.00), null, null, null))
        );
        String hodReqRes = mockMvc.perform(post("/api/store/requisitions")
                .header("Authorization", "Bearer " + hodToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hodCreateReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID hodReqId = UUID.fromString(objectMapper.readTree(hodReqRes).get("id").asText());
        mockMvc.perform(post("/api/store/requisitions/" + hodReqId + "/submit")
                .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk());

        // HOD attempts self-approval -> MAKER_CHECKER_VIOLATION
        mockMvc.perform(post("/api/store/requisitions/" + hodReqId + "/approvals/decision")
                .header("Authorization", "Bearer " + hodToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequisitionDto.DecisionRequest("APPROVE", "Self approval attempt", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // 4. Check Pending Approvals as HOD
        mockMvc.perform(get("/api/store/requisitions/approvals/pending")
                .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + reqId + "')]").exists());

        // 5. HOD approves Step 1 (with quantity adjustment)
        RequisitionDto.DecisionRequest hodDecision = new RequisitionDto.DecisionRequest(
                "APPROVE", "Approved by HOD with qty 8",
                List.of(new RequisitionDto.LineDecision(1, BigDecimal.valueOf(8), "Adjusted qty"))
        );

        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/approvals/decision")
                .header("Authorization", "Bearer " + hodToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(hodDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_APPROVAL"))
                .andExpect(jsonPath("$.workflow.status").value("RUNNING"))
                .andExpect(jsonPath("$.workflow.currentStepNo").value(2))
                .andExpect(jsonPath("$.items[0].approvedQty").value(8.000));

        // 6. Step 2: Store Officer verifies and gives final approval
        RequisitionDto.DecisionRequest storeOfficerDecision = new RequisitionDto.DecisionRequest(
                "APPROVE", "Stock verified in central warehouse. Recommended for issue.", null
        );

        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/approvals/decision")
                .header("Authorization", "Bearer " + storeOfficerToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(storeOfficerDecision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.workflow.status").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].lineStatus").value("APPROVED"));

        // 7. Verify Audit Events written in audit.event
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit.event WHERE entity_type = 'REQUISITION' AND entity_id = ?",
                Integer.class, reqId
        );
        assertThat(auditCount).isNotNull().isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Rejection Workflow: Submitted Requisition rejected by HOD terminates workflow")
    void testRejectionWorkflow() throws Exception {
        // Create draft
        RequisitionDto.CreateRequest createReq = new RequisitionDto.CreateRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "IT-DEPT", "IT Dept",
                null, null, null, null, null,
                "Need high-end graphics cards", "NORMAL", LocalDate.now().plusDays(10),
                List.of(new RequisitionDto.LineRequest(testItemId, BigDecimal.valueOf(2), BigDecimal.valueOf(500.00), null, null, null))
        );

        String createRes = mockMvc.perform(post("/api/store/requisitions")
                .header("Authorization", "Bearer " + employeeToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID reqId = UUID.fromString(objectMapper.readTree(createRes).get("id").asText());

        // Submit
        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/submit")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk());

        // Reject by HOD
        RequisitionDto.DecisionRequest rejectReq = new RequisitionDto.DecisionRequest(
                "REJECT", "Budget insufficient for current quarter", null
        );

        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/approvals/decision")
                .header("Authorization", "Bearer " + hodToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.workflow.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Return and Resubmit Workflow: HOD returns -> Requester edits and resubmits")
    void testReturnAndResubmitWorkflow() throws Exception {
        // Create draft
        RequisitionDto.CreateRequest createReq = new RequisitionDto.CreateRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "IT-DEPT", "IT Dept",
                null, null, null, null, null,
                "Keyboard replacements", "NORMAL", LocalDate.now().plusDays(10),
                List.of(new RequisitionDto.LineRequest(testItemId, BigDecimal.valueOf(5), BigDecimal.valueOf(100.00), null, null, null))
        );

        String createRes = mockMvc.perform(post("/api/store/requisitions")
                .header("Authorization", "Bearer " + employeeToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(createRes);
        UUID reqId = UUID.fromString(json.get("id").asText());
        Long version = json.get("version").asLong();

        // Submit
        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/submit")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk());

        // Return by HOD
        RequisitionDto.DecisionRequest returnReq = new RequisitionDto.DecisionRequest(
                "RETURN", "Please provide detailed justification and reduced count", null
        );

        String returnedRes = mockMvc.perform(post("/api/store/requisitions/" + reqId + "/approvals/decision")
                .header("Authorization", "Bearer " + hodToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.workflow.status").value("RETURNED"))
                .andReturn().getResponse().getContentAsString();
        Long newVersion = objectMapper.readTree(returnedRes).get("version").asLong();

        // Requester updates returned requisition
        RequisitionDto.UpdateRequest updateReq = new RequisitionDto.UpdateRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "IT-DEPT", "IT Dept",
                null, null, null, null, null,
                "Keyboard replacements with justification", "NORMAL", LocalDate.now().plusDays(10),
                List.of(new RequisitionDto.LineRequest(testItemId, BigDecimal.valueOf(3), BigDecimal.valueOf(100.00), "Standard", "3 keyboards damaged during transit", "Dell")),
                newVersion
        );

        mockMvc.perform(put("/api/store/requisitions/" + reqId)
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // Resubmit
        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/submit")
                .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_APPROVAL"))
                .andExpect(jsonPath("$.workflow.status").value("RUNNING"))
                .andExpect(jsonPath("$.workflow.currentStepNo").value(1));
    }

    @Test
    @DisplayName("Requester Cancellation: Requisition in draft or pending can be cancelled by requester")
    void testRequesterCancellation() throws Exception {
        RequisitionDto.CreateRequest createReq = new RequisitionDto.CreateRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "IT-DEPT", "IT Dept",
                null, null, null, null, null,
                "Accidentally created requisition", "LOW", LocalDate.now().plusDays(5),
                List.of(new RequisitionDto.LineRequest(testItemId, BigDecimal.valueOf(1), BigDecimal.valueOf(50.00), null, null, null))
        );

        String createRes = mockMvc.perform(post("/api/store/requisitions")
                .header("Authorization", "Bearer " + employeeToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID reqId = UUID.fromString(objectMapper.readTree(createRes).get("id").asText());

        // Cancel
        mockMvc.perform(post("/api/store/requisitions/" + reqId + "/cancel")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequisitionDto.CancelRequest("No longer required"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
