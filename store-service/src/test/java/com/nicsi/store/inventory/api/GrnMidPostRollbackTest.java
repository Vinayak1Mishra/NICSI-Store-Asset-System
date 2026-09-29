package com.nicsi.store.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.grn.repository.GrnRepository;
import com.nicsi.store.inspection.dto.InspectionDto;
import com.nicsi.store.procurementref.dto.PurchaseOrderDto;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 checklist item 1, GRN flavour: a multi-line GRN post is ONE transaction.
 *
 * Line 1 is a valid serialised item; line 2 supplies a serial number that is ALREADY registered
 * against that item, so the insert violates uq_asset_item_serial -- but only after line 1 has
 * already written its ledger row and created its asset. Nothing from the failed post may survive,
 * and the Idempotency-Key must not be consumed, so the same key can be retried once the data is
 * corrected.
 *
 * Isolation: option A. Unique item/store/location codes, no table wipes, every assertion scoped to
 * this test's own ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GrnMidPostRollbackTest extends BaseIntegrationTest {

    private static final String DUPLICATE_SERIAL = "PRE-EXISTING-SN-1";
    private static final String CORRECTED_SERIAL = "CORRECTED-SN-1";
    private static final String LINE1_SERIAL = "LINE1-SN-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private InventoryTestFixtures fixtures;
    @Autowired
    private GrnRepository grnRepository;

    @Test
    @DisplayName("Duplicate serial on line 2 rolls back line 1 entirely and leaves the Idempotency-Key usable")
    void duplicateSerialOnLineTwoRollsBackEverythingAndPreservesIdempotencyKey() throws Exception {
        String operator = token("store.operator");
        String manager = token("store.manager");
        String admin = token("admin");

        Ctx line1 = fixtures.newSerialised("GrnRb-L1");
        Ctx line2 = fixtures.newSerialised("GrnRb-L2");

        // An asset for line2's item already carries DUPLICATE_SERIAL, as if it had been
        // registered by an earlier GRN. Posting line 2 with that same serial must collide.
        seedPreExistingAsset(line2, line1, DUPLICATE_SERIAL);

        // --- PO
        String poNumber = "PO-RB-" + UUID.randomUUID();
        String poResp = mockMvc.perform(post("/api/store/purchase-orders")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PurchaseOrderDto.CreateRequest(
                                "NICSI_ERP", null, poNumber, LocalDate.now(),
                                "DIRECT", "GEM-RB-0001", "CONT-RB",
                                UUID.randomUUID(), "VEND-RB", "Vendor RB",
                                "INR", new BigDecimal("2000.00"), null,
                                List.of(
                                        new PurchaseOrderDto.CreateItemRequest(1, line1.item().getId(), "L1", new BigDecimal("1.000"), new BigDecimal("1000.00"), BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null),
                                        new PurchaseOrderDto.CreateItemRequest(2, line2.item().getId(), "L2", new BigDecimal("1.000"), new BigDecimal("1000.00"), BigDecimal.ZERO, LocalDate.now().plusDays(30), null, null, null)
                                )))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode po = objectMapper.readTree(poResp);
        UUID poId = UUID.fromString(po.get("id").asText());
        UUID poLine1 = UUID.fromString(po.get("items").get(0).get("id").asText());
        UUID poLine2 = UUID.fromString(po.get("items").get(1).get("id").asText());

        // --- GRN with two serialised lines, created and received by store.manager
        String grnResp = mockMvc.perform(post("/api/store/grns")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GrnDto.CreateRequest(
                                LocalDate.now(), line1.store().getId(), poId,
                                UUID.randomUUID(), "VEND-RB",
                                "INV-RB-1", LocalDate.now(), "CH-RB-1", LocalDate.now(),
                                "mid-post rollback probe",
                                List.of(
                                        new GrnDto.CreateItemRequest(poLine1, line1.item().getId(), new BigDecimal("1.000"), new BigDecimal("1000.00"), line1.location().getId(), null, null, null, "line one"),
                                        new GrnDto.CreateItemRequest(poLine2, line2.item().getId(), new BigDecimal("1.000"), new BigDecimal("1000.00"), line1.location().getId(), null, null, null, "line two")
                                )))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode grnNode = objectMapper.readTree(grnResp);
        UUID grnId = UUID.fromString(grnNode.get("id").asText());
        UUID grnLine1 = UUID.fromString(grnNode.get("items").get(0).get("id").asText());
        UUID grnLine2 = UUID.fromString(grnNode.get("items").get(1).get("id").asText());

        mockMvc.perform(post("/api/store/grns/" + grnId + "/submit")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_INSPECTION"));

        // --- Inspection accepted by admin (admin is neither receiver nor creator)
        String inspJson = mockMvc.perform(MockMvcRequestBuilders.get("/api/store/inspections/by-grn/" + grnId)
                        .header("Authorization", "Bearer " + manager))
                .andReturn().getResponse().getContentAsString();
        JsonNode insp = objectMapper.readTree(inspJson);
        String inspectionId = insp.get("id").asText();
        String technical = "{\"specificationMatch\":true,\"physicalCondition\":\"GOOD\"}";

        mockMvc.perform(post("/api/store/inspections/" + inspectionId + "/decide")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InspectionDto.DecideRequest(
                                "accepted",
                                List.of(
                                        new InspectionDto.DecideItemRequest(UUID.fromString(insp.get("items").get(0).get("id").asText()), new BigDecimal("1.000"), BigDecimal.ZERO, BigDecimal.ZERO, true, "GOOD", true, true, technical, "ok"),
                                        new InspectionDto.DecideItemRequest(UUID.fromString(insp.get("items").get(1).get("id").asText()), new BigDecimal("1.000"), BigDecimal.ZERO, BigDecimal.ZERO, true, "GOOD", true, true, technical, "ok")
                                ),
                                insp.get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("ACCEPTED");

        // Baselines taken immediately before the post.
        long auditBefore = auditCount();
        assertThat(ledgerRowsForGrn(grnId)).as("precondition: no ledger rows yet").isZero();
        assertThat(balanceRowCount(line1, line1)).as("precondition: line 1 item has no balance row yet").isZero();
        assertThat(balanceRowCount(line2, line1)).as("precondition: line 2 item has no balance row yet").isZero();

        // --- Post with a duplicate serial on line 2. Line 1 writes first, then line 2 collides.
        String key = "IDEM-GRN-RB-" + UUID.randomUUID();
        GrnPostDto.PostRequest badRequest2 = new GrnPostDto.PostRequest(
                List.of(
                        new GrnPostDto.LineSerialRequest(grnLine1, List.of(LINE1_SERIAL)),
                        new GrnPostDto.LineSerialRequest(grnLine2, List.of(DUPLICATE_SERIAL))
                ),
                "will fail on line 2");

        // 5xx rather than 4xx because DataIntegrityViolationException is mapped to 500 today
        // (see api-contract.md D1). When D1 is fixed this assertion must be revisited.
        mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest2)))
                .andExpect(status().is5xxServerError());

        // 1. No ledger row for this GRN -- not even the one line 1 "succeeded" on.
        assertThat(ledgerRowsForGrn(grnId)).as("no ledger rows committed for the failed GRN").isZero();
        // 2. No asset created for either GRN line.
        assertThat(assetCountForGrnItem(grnLine1)).as("line 1 asset rolled back").isZero();
        assertThat(assetCountForGrnItem(grnLine2)).as("line 2 asset rolled back").isZero();
        // 3. Neither balance row was created.
        assertThat(balanceRowCount(line1, line1)).as("line 1 balance unchanged").isZero();
        assertThat(balanceRowCount(line2, line1)).as("line 2 balance unchanged").isZero();
        // 4. GRN is still not posted.
        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus())
                .as("GRN must not be POSTED after rollback")
                .isIn("ACCEPTED", "PARTIALLY_ACCEPTED");
        // 5. No audit row for the post.
        assertThat(auditCount()).as("no audit row committed for the failed post").isEqualTo(auditBefore);
        // The pre-existing asset is untouched -- the collision must not have deleted it.
        assertThat(serialAssetCount(line2.item().getId(), DUPLICATE_SERIAL)).isEqualTo(1L);

        // 6. The Idempotency-Key was NOT consumed. Retrying the SAME key with corrected data must
        //    succeed, proving the failed attempt left no idempotency state behind.
        GrnPostDto.PostRequest corrected = new GrnPostDto.PostRequest(
                List.of(
                        new GrnPostDto.LineSerialRequest(grnLine1, List.of(LINE1_SERIAL)),
                        new GrnPostDto.LineSerialRequest(grnLine2, List.of(CORRECTED_SERIAL))
                ),
                "corrected on retry");

        mockMvc.perform(post("/api/store/grns/" + grnId + "/post")
                        .header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corrected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalPostedLines").value(2))
                .andExpect(jsonPath("$.totalAssetsCreated").value(2));

        // And the retry really did post both lines.
        assertThat(ledgerRowsForGrn(grnId)).as("retry posted both lines").isEqualTo(2L);
        assertThat(assetCountForGrnItem(grnLine1)).isEqualTo(1L);
        assertThat(assetCountForGrnItem(grnLine2)).isEqualTo(1L);
        assertThat(onHand(line1, line1)).isEqualByComparingTo("1.000");
        assertThat(grnRepository.findById(grnId).orElseThrow().getStatus()).isEqualTo("POSTED");
    }

    private void seedPreExistingAsset(Ctx itemCtx, Ctx locCtx, String serial) {
        jdbcTemplate.update(
                "INSERT INTO store.asset (asset_code, item_id, serial_number, store_id, location_id, qr_code_value, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ASSET-PRE-" + fixtures.tag(),
                itemCtx.item().getId(), serial,
                locCtx.store().getId(), locCtx.location().getId(),
                "QR-PRE-" + UUID.randomUUID(),
                UUID.nameUUIDFromBytes("admin".getBytes()));
    }

    private long serialAssetCount(UUID itemId, String serial) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store.asset WHERE item_id = ? AND serial_number = ?",
                Long.class, itemId, serial);
    }

    private long ledgerRowsForGrn(UUID grnId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store.stock_transaction WHERE reference_type = 'GRN' AND reference_id = ?",
                Long.class, grnId);
    }

    private long assetCountForGrnItem(UUID grnItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store.asset WHERE grn_item_id = ?", Long.class, grnItemId);
    }

    private long balanceRowCount(Ctx itemCtx, Ctx locCtx) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                Long.class, itemCtx.item().getId(), locCtx.store().getId(), locCtx.location().getId());
    }

    private BigDecimal onHand(Ctx itemCtx, Ctx locCtx) {
        return jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, itemCtx.item().getId(), locCtx.store().getId(), locCtx.location().getId());
    }

    private long auditCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit.event", Long.class);
    }

    private String token(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode n = objectMapper.readTree(resp);
        return n.get("token").asText();
    }
}
