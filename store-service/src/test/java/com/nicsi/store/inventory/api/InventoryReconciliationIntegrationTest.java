package com.nicsi.store.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.inventory.dto.InventoryDto;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 checklist item 10: reconciliation.
 *
 * The single database is shared with other tests, so this test NEVER asserts that the whole system
 * is clean. It injects a known variance into exactly one stock_balance row it owns, asserts that
 * reconciliation reports that one row with the right numbers, asserts that the other rows this
 * test created are NOT reported, then restores the row and asserts the report goes quiet again.
 * Any pre-existing discrepancy belonging to another test is ignored by filtering on this test's
 * own item ids.
 *
 * Isolation: option A. Unique item/store/location codes, no table wipes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InventoryReconciliationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private InventoryTestFixtures fixtures;

    @Test
    @DisplayName("Injected ledger/balance variance is reported with exact numbers, then cleared on restore")
    void reportsOnlyThisTestsInjectedVarianceAndClearsOnRestore() throws Exception {
        String token = token("admin");
        UUID adminId = UUID.nameUUIDFromBytes("admin".getBytes());

        // Control: a row this test owns that must NEVER be reported.
        Ctx clean = fixtures.newConsumable("Recon-Clean");
        fixtures.receive(clean, "40", "12.00", adminId);

        // Target: a row this test owns into which we inject a variance.
        Ctx drifted = fixtures.newConsumable("Recon-Drift");
        fixtures.receive(drifted, "100", "5.00", adminId);

        // Serialised: on-hand stock with no matching AVAILABLE assets -> second check fires.
        Ctx serialised = fixtures.newSerialised("Recon-Serial");
        fixtures.receive(serialised, "3", "200.00", adminId);

        UUID cleanItem = clean.item().getId();
        UUID driftedItem = drifted.item().getId();
        UUID serialisedItem = serialised.item().getId();

        // --- Inject the variance: corrupt the balance so it no longer equals the ledger sum.
        // The ledger is immutable, so the only way to create a real mismatch is to move the
        // balance, exactly as a bad migration or a manual DB edit would.
        BigDecimal ledgerNet = ledgerNet(drifted);
        assertThat(ledgerNet).as("precondition: ledger says 100").isEqualByComparingTo("100.000");
        BigDecimal corrupted = new BigDecimal("107.000");
        setOnHand(drifted, corrupted);

        // --- Run 1: the drift must be reported.
        List<InventoryDto.Discrepancy> mine = discrepanciesFor(token, cleanItem, driftedItem, serialisedItem);

        List<InventoryDto.Discrepancy> drift = mine.stream()
                .filter(d -> d.itemId().equals(driftedItem) && "LEDGER_BALANCE_MISMATCH".equals(d.type()))
                .toList();
        assertThat(drift).as("the injected variance must be reported").hasSize(1);

        InventoryDto.Discrepancy d = drift.get(0);
        assertThat(d.ledgerCalculatedQty()).as("ledger side, from the immutable ledger")
                .isEqualByComparingTo("100.000");
        assertThat(d.balanceOnHandQty()).as("balance side, from stock_balance")
                .isEqualByComparingTo("107.000");
        assertThat(d.itemCode()).isEqualTo(drifted.item().getItemCode());
        assertThat(d.storeId()).isEqualTo(drifted.store().getId());
        assertThat(d.locationId()).isEqualTo(drifted.location().getId());
        assertThat(d.description()).contains("100.000").contains("107.000");

        // The control row this test owns must not be reported at all.
        assertThat(mine).as("clean row this test owns must not be reported")
                .noneMatch(x -> x.itemId().equals(cleanItem));

        // The serialised row is a genuinely different finding (Check 2), reported independently.
        assertThat(mine).as("serialised row reports the asset-count finding")
                .anyMatch(x -> x.itemId().equals(serialisedItem)
                        && "SERIALISED_ASSET_COUNT_MISMATCH".equals(x.type()));

        // --- Restore the balance, exactly as an operator would correct the count.
        setOnHand(drifted, ledgerNet);

        // --- Run 2: the drift is gone.
        List<InventoryDto.Discrepancy> afterRestore =
                discrepanciesFor(token, cleanItem, driftedItem, serialisedItem);

        assertThat(afterRestore).as("restored row must no longer be reported")
                .noneMatch(x -> x.itemId().equals(driftedItem) && "LEDGER_BALANCE_MISMATCH".equals(x.type()));
        assertThat(afterRestore).as("clean row still not reported after restore")
                .noneMatch(x -> x.itemId().equals(cleanItem));

        // The ledger never moved, which is the whole point of the immutable-ledger design.
        assertThat(ledgerNet(drifted)).as("ledger untouched by the injection and the restore")
                .isEqualByComparingTo("100.000");
    }

    /** Calls GET /api/store/inventory/reconciliation and keeps only rows for this test's items. */
    private List<InventoryDto.Discrepancy> discrepanciesFor(
            String token, UUID cleanItem, UUID driftedItem, UUID serialisedItem) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/store/inventory/reconciliation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        List<InventoryDto.Discrepancy> all = objectMapper.convertValue(
                root.get("discrepancies"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, InventoryDto.Discrepancy.class));

        return all.stream()
                .filter(d -> d.itemId().equals(cleanItem)
                        || d.itemId().equals(driftedItem)
                        || d.itemId().equals(serialisedItem))
                .toList();
    }

    private BigDecimal ledgerNet(Ctx ctx) {
        BigDecimal v = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_in),0) - COALESCE(SUM(quantity_out),0) FROM store.stock_transaction "
                        + "WHERE item_id = ? AND store_id = ? AND location_id = ? AND lot_id IS NULL",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        return v == null ? BigDecimal.ZERO : v;
    }

    private void setOnHand(Ctx ctx, BigDecimal value) {
        jdbcTemplate.update(
                "UPDATE store.stock_balance SET on_hand_qty = ? WHERE item_id = ? AND store_id = ? AND location_id = ?",
                value, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
    }

    private String token(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("token").asText();
    }
}
