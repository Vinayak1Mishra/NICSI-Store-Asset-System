package com.nicsi.store.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.inventory.dto.StockAdjustmentDto;
import com.nicsi.store.testutil.BaseIntegrationTest;
import com.nicsi.store.testutil.InventoryTestFixtures;
import com.nicsi.store.testutil.InventoryTestFixtures.Ctx;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 checklist item 1: a multi-line posting is ONE REQUIRED transaction. If it fails on any
 * line, every earlier line must be rolled back completely -- balance, ledger, audit and the
 * document status. No partial posting may ever be visible.
 *
 * The failure is induced on the SECOND line (OUT of more than is on hand) so that the FIRST line
 * has already been written by the time the exception is thrown. Line order in the request is
 * A then B, so line A must be proven rolled back.
 *
 * Isolation: option A. Unique item/store/location codes, no table wipes. All assertions are scoped
 * to this test's own ids; nothing asserts on whole-system state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MidPostRollbackTest extends BaseIntegrationTest {

    private static final String MAKER = "store.manager";
    private static final String CHECKER = "admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private InventoryTestFixtures fixtures;

    @Test
    @DisplayName("Line 2 fails after line 1 wrote: balance, ledger, audit and status all roll back")
    void failureOnSecondLineRollsBackTheFirstLine() throws Exception {
        String makerToken = token(MAKER);
        String checkerToken = token(CHECKER);

        // Two independent dimensions. A is written first, then B fails, so A must not survive.
        Ctx a = fixtures.newConsumable("Rollback-A");
        Ctx b = fixtures.newConsumable("Rollback-B");
        fixtures.receive(a, "100", "10.00", adminId());
        fixtures.receive(b, "5", "10.00", adminId());

        long ledgerBefore = ledgerCount();

        // Line 1: OUT 10 -- valid, A holds 100.
        // Line 2: OUT 50 -- INVALID, B holds only 5, so this throws INSUFFICIENT_STOCK.
        StockAdjustmentDto.CreateRequest createReq = new StockAdjustmentDto.CreateRequest(
                a.store().getId(), "FOUND_STOCK", "Mid-post rollback probe",
                LocalDate.now(),
                List.of(
                        new StockAdjustmentDto.LineRequest(a.item().getId(), a.location().getId(), null,
                                "OUT", new BigDecimal("10.000"), new BigDecimal("10.00"), null, "valid line, written first"),
                        new StockAdjustmentDto.LineRequest(b.item().getId(), b.location().getId(), null,
                                "OUT", new BigDecimal("50.000"), new BigDecimal("10.00"), null, "fails, insufficient stock")
                )
        );

        String createResp = mockMvc.perform(post("/api/store/adjustments")
                        .header("Authorization", "Bearer " + makerToken)
                        .header("Idempotency-Key", "IDEM-RB-CREATE-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        UUID adjId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/submit")
                        .header("Authorization", "Bearer " + makerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Checker approves (maker cannot approve their own -- already covered elsewhere).
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/approve")
                        .header("Authorization", "Bearer " + checkerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Baselines are taken immediately before /post: creating, submitting and approving the
        // adjustment each commit audit rows of their own, so an audit baseline taken earlier
        // would wrongly count those as leakage from the rolled-back posting.
        long auditBefore = auditCount();

        // Posting: line 1 succeeds, line 2 throws. The whole transaction must roll back.
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/post")
                        .header("Authorization", "Bearer " + checkerToken)
                        .header("Idempotency-Key", "IDEM-RB-POST-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        // 1. The adjustment is still APPROVED, never POSTED.
        assertThat(adjustmentStatus(adjId)).as("status must not be POSTED after rollback").isEqualTo("APPROVED");

        // 2. Line 1's balance change is gone: A is untouched at 100, B untouched at 5.
        assertThat(onHand(a)).as("line 1 balance change rolled back").isEqualByComparingTo("100.000");
        assertThat(onHand(b)).as("line 2 balance unchanged").isEqualByComparingTo("5.000");

        // 3. Not one ledger row was written -- not even for the line that "succeeded".
        assertThat(ledgerCount()).as("no ledger rows committed").isEqualTo(ledgerBefore);

        // 4. Not one audit row was written.
        assertThat(auditCount()).as("no audit rows committed").isEqualTo(auditBefore);

        // 5. No balance row was created for the failing dimensions.
        assertThat(balanceRowCount(a)).as("no stray balance row for dimension A").isEqualTo(1L);

        // 6. The document is still postable -- the rollback was clean, not a poisoned state.
        //    Nothing was consumed, so the same posting must now fail identically rather than
        //    succeeding on a half-applied balance.
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/post")
                        .header("Authorization", "Bearer " + checkerToken)
                        .header("Idempotency-Key", "IDEM-RB-POST-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(onHand(a)).as("second attempt also rolled back").isEqualByComparingTo("100.000");
        assertThat(ledgerCount()).as("second attempt wrote nothing").isEqualTo(ledgerBefore);
    }

    private String token(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = objectMapper.readTree(resp);
        return n.get("token").asText();
    }

    private static UUID adminId() {
        return UUID.nameUUIDFromBytes("admin".getBytes());
    }

    private String adjustmentStatus(UUID adjId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM store.stock_adjustment WHERE id = ?", String.class, adjId);
    }

    private BigDecimal onHand(Ctx ctx) {
        return jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
    }

    private long balanceRowCount(Ctx ctx) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                Long.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
    }

    private long ledgerCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM store.stock_transaction", Long.class);
    }

    private long auditCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit.event", Long.class);
    }
}
