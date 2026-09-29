package com.nicsi.store.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.dto.StockAdjustmentDto;
import com.nicsi.store.inventory.repository.StockAdjustmentRepository;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemCategory;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemCategoryRepository;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.master.repository.UomRepository;
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StockAdjustmentAndReversalIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StoreSiteRepository storeSiteRepository;

    @Autowired
    private StorageLocationRepository storageLocationRepository;

    @Autowired
    private ItemCategoryRepository itemCategoryRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UomRepository uomRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private StockTransactionRepository stockTransactionRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    // STOCK_ADJUST is required by every /adjustments write endpoint. Only two of the
    // eleven PDF 18.1 mock users hold it: store.manager and admin. They are used as the
    // maker and the checker respectively so the maker-checker 403s stay real.
    private String storeManagerToken;
    private String adminToken;

    private UUID testStoreId;
    private UUID testLocationId;
    private UUID testItemId;

    @BeforeEach
    void setUp() throws Exception {
        // Guarded TRUNCATE replaces the old DELETE statements, which could never work once
        // a run had posted to the ledger: store.stock_transaction has the
        // trg_stock_txn_immutable BEFORE UPDATE OR DELETE row trigger. TRUNCATE bypasses
        // row-level triggers, which is safe only on nicsi_store_test -- the helper asserts
        // that before touching anything. Step 9 still asserts that UPDATE and DELETE on
        // store.stock_transaction are rejected by that trigger.
        truncateInventoryTables(jdbcTemplate, "StockAdjustmentAndReversalIntegrationTest");

        storeManagerToken = getToken("store.manager");
        adminToken = getToken("admin");

        StoreSite store = storeSiteRepository.findByStoreCodeIgnoreCase("MAIN-STORE").orElseGet(() -> {
            StoreSite s = new StoreSite();
            s.setStoreCode("MAIN-STORE");
            s.setStoreName("Main Warehouse");
            s.setStoreType("GENERAL");
            return storeSiteRepository.save(s);
        });
        testStoreId = store.getId();

        StorageLocation location = storageLocationRepository.findAll().stream()
                .filter(l -> l.getStore().getId().equals(testStoreId) && "RACK-01".equals(l.getLocationCode()))
                .findFirst().orElseGet(() -> {
                    StorageLocation l = new StorageLocation();
                    l.setStore(store);
                    l.setLocationCode("RACK-01");
                    l.setLocationName("Rack 01");
                    l.setLocationType("RACK");
                    return storageLocationRepository.save(l);
                });
        testLocationId = location.getId();

        Uom uom = uomRepository.findByUomCodeIgnoreCase("NOS").orElseThrow();

        ItemCategory cat = itemCategoryRepository.findByCategoryCodeIgnoreCase("GEN").orElseGet(() -> {
            ItemCategory c = new ItemCategory();
            c.setCategoryCode("GEN");
            c.setCategoryName("General Items");
            return itemCategoryRepository.save(c);
        });

        Item item = itemRepository.findByItemCodeIgnoreCase("A4-PAPER").orElseGet(() -> {
            Item it = new Item();
            it.setItemCode("A4-PAPER");
            it.setItemName("A4 Copier Paper Ream");
            it.setCategory(cat);
            it.setBaseUom(uom);
            it.setItemType("CONSUMABLE");
            it.setTrackingType("QUANTITY");
            it.setStandardRate(new BigDecimal("350.00"));
            return itemRepository.save(it);
        });
        testItemId = item.getId();
    }

    private String getToken(String username) throws Exception {
        String resp = mockMvc.perform(post("/api/store/dev/token?username=" + username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(resp);
        return node.get("token").asText();
    }

    @Test
    @DisplayName("Stock Adjustment Workflow: Draft -> Submit -> Maker-Checker Approval/Posting -> Ledger Updated -> Reversal")
    void testStockAdjustmentAndReversalFlow() throws Exception {
        // Step 1: Create Draft Adjustment as the MAKER (Store Manager) (IN: 50 units @ 300.00)
        StockAdjustmentDto.CreateRequest createReq = new StockAdjustmentDto.CreateRequest(
                testStoreId, "FOUND_STOCK", "Found unopened surplus box during shelf audit",
                LocalDate.now(),
                List.of(new StockAdjustmentDto.LineRequest(
                        testItemId, testLocationId, null, "IN",
                        new BigDecimal("50.000"), new BigDecimal("300.00"), null, "Shelf 2 discovery"
                ))
        );

        String createResp = mockMvc.perform(post("/api/store/adjustments")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", "IDEM-ADJ-CREATE-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        UUID adjId = UUID.fromString(objectMapper.readTree(createResp).get("id").asText());

        // Step 2: Submit Adjustment
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/submit")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // Step 3: MAKER-CHECKER - Creator (Store Manager) tries to APPROVE -> MUST FAIL 403
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/approve")
                        .header("Authorization", "Bearer " + storeManagerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // Step 4: Independent user (Admin) Approves
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Step 5: MAKER-CHECKER - Creator tries to POST -> MUST FAIL 403
        String postKey = "IDEM-ADJ-POST-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/post")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", postKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // Step 6: Admin (independent of the maker) Posts Adjustment
        mockMvc.perform(post("/api/store/adjustments/" + adjId + "/post")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", postKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        // Verify Stock Balance
        List<StockBalance> balances = stockBalanceRepository.findByStoreIdAndItemId(testStoreId, testItemId);
        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).getOnHandQty()).isEqualByComparingTo("50.000");
        assertThat(balances.get(0).getAvgUnitCost()).isEqualByComparingTo("300.0000");

        // Verify Stock Transaction Ledger
        List<StockTransaction> txns = stockTransactionRepository.findByReferenceTypeAndReferenceId("STOCK_ADJUSTMENT", adjId);
        assertThat(txns).hasSize(1);
        StockTransaction adjustmentTxn = txns.get(0);
        assertThat(adjustmentTxn.getTransactionType()).isEqualTo("ADJUSTMENT_IN");
        assertThat(adjustmentTxn.getQuantityIn()).isEqualByComparingTo("50.000");

        // Step 7: REVERSAL - Original poster (Admin) cannot reverse their own transaction (Maker-Checker)
        mockMvc.perform(post("/api/store/adjustments/" + adjustmentTxn.getId() + "/reversal")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "IDEM-REV-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockAdjustmentDto.ReversalRequest("Incorrect count"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CHECKER_VIOLATION"));

        // Step 8: Store Manager (not the poster) reverses transaction
        mockMvc.perform(post("/api/store/adjustments/" + adjustmentTxn.getId() + "/reversal")
                        .header("Authorization", "Bearer " + storeManagerToken)
                        .header("Idempotency-Key", "IDEM-REV-" + System.currentTimeMillis())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockAdjustmentDto.ReversalRequest("Incorrect count entered"))))
                .andExpect(status().isOk());

        // Verify Balance restored to 0
        StockBalance balanceAfterReversal = stockBalanceRepository.findByDimensions(testItemId, testStoreId, testLocationId, null).orElseThrow();
        assertThat(balanceAfterReversal.getOnHandQty()).isEqualByComparingTo("0.000");

        // Step 9: IMMUTABILITY DB TRIGGER TEST
        // Direct UPDATE or DELETE on stock_transaction MUST BE PREVENTED BY POSTGRES TRIGGER trg_stock_txn_immutable
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE store.stock_transaction SET quantity_in = 999 WHERE id = ?", adjustmentTxn.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM store.stock_transaction WHERE id = ?", adjustmentTxn.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("immutable");
    }
}
