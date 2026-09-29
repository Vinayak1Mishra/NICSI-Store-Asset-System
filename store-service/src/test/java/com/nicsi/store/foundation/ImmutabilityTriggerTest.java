package com.nicsi.store.foundation;

import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the database immutability triggers on stock_transaction and
 * audit.event correctly block UPDATE and DELETE while allowing INSERT.
 *
 * Uses the exact column names from the SQL schema.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutabilityTriggerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testStockTransactionInsertSucceeds() {
        UUID txId = insertPrerequisitesAndStockTransaction();
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM store.stock_transaction WHERE id = ?", Integer.class, txId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testStockTransactionUpdateBlocked() {
        UUID txId = insertPrerequisitesAndStockTransaction();

        assertThatThrownBy(() ->
            jdbcTemplate.update("UPDATE store.stock_transaction SET remarks = 'Modified' WHERE id = ?", txId)
        ).hasMessageContaining("immutable");
    }

    @Test
    void testStockTransactionDeleteBlocked() {
        UUID txId = insertPrerequisitesAndStockTransaction();

        assertThatThrownBy(() ->
            jdbcTemplate.update("DELETE FROM store.stock_transaction WHERE id = ?", txId)
        ).hasMessageContaining("immutable");
    }

    @Test
    void testAuditEventInsertSucceeds() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO audit.event (id, event_time, actor_user_id, actor_username, actor_role, " +
            "module, action, entity_type, entity_id, correlation_id) " +
            "VALUES (?, now(), ?, 'test-user', 'ROLE_ADMIN', 'STORE', 'CREATE', 'TEST', ?, 'corr-1')",
            eventId, UUID.randomUUID(), UUID.randomUUID()
        );
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit.event WHERE id = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testAuditEventUpdateBlocked() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO audit.event (id, event_time, actor_user_id, actor_username, actor_role, " +
            "module, action, entity_type, entity_id) " +
            "VALUES (?, now(), ?, 'test-user', 'ROLE_ADMIN', 'STORE', 'CREATE', 'TEST', ?)",
            eventId, UUID.randomUUID(), UUID.randomUUID()
        );

        assertThatThrownBy(() ->
            jdbcTemplate.update("UPDATE audit.event SET action = 'UPDATE' WHERE id = ?", eventId)
        ).hasMessageContaining("immutable");
    }

    @Test
    void testAuditEventDeleteBlocked() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO audit.event (id, event_time, actor_user_id, actor_username, actor_role, " +
            "module, action, entity_type, entity_id) " +
            "VALUES (?, now(), ?, 'test-user', 'ROLE_ADMIN', 'STORE', 'CREATE', 'TEST', ?)",
            eventId, UUID.randomUUID(), UUID.randomUUID()
        );

        assertThatThrownBy(() ->
            jdbcTemplate.update("DELETE FROM audit.event WHERE id = ?", eventId)
        ).hasMessageContaining("immutable");
    }

    /**
     * Inserts the minimum prerequisite data needed for a stock_transaction row,
     * using the EXACT column names from the SQL schema.
     */
    private UUID insertPrerequisitesAndStockTransaction() {
        // We use existing UOM seed data (NOS)
        UUID uomId = jdbcTemplate.queryForObject(
            "SELECT id FROM store.uom WHERE uom_code = 'NOS'", UUID.class);

        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO store.item_category (id, category_code, category_name, description, sort_order, active) " +
            "VALUES (?, 'TEST-CAT', 'Test Category', 'Test', 0, true)", categoryId);

        UUID itemId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO store.item (id, item_code, item_name, category_id, base_uom_id, " +
            "item_type, tracking_type, active) " +
            "VALUES (?, 'ITM-IMMUT-TEST', 'Test Item', ?, ?, 'CONSUMABLE', 'QUANTITY', true)",
            itemId, categoryId, uomId);

        UUID storeId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO store.store_site (id, store_code, store_name, store_type, active) " +
            "VALUES (?, 'STORE-IMMUT', 'Test Store', 'GENERAL', true)", storeId);

        UUID locId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO store.storage_location (id, store_id, location_code, location_name, location_type, active) " +
            "VALUES (?, ?, 'LOC-IMMUT', 'Test Location', 'ROOM', true)", locId, storeId);

        UUID txId = UUID.randomUUID();
        UUID txNo = UUID.randomUUID(); // unique transaction_no
        jdbcTemplate.update(
            "INSERT INTO store.stock_transaction (id, transaction_no, transaction_type, transaction_time, " +
            "item_id, store_id, location_id, quantity_in, quantity_out, unit_cost, total_cost, " +
            "reference_type, reference_id, posted_by, posted_at, remarks) " +
            "VALUES (?, ?, 'RECEIPT', now(), ?, ?, ?, 10.000, 0.000, 100.0000, 1000.00, " +
            "'GRN', ?, ?, now(), 'Test Transaction')",
            txId, "TXN-IMMUT-" + txNo.toString().substring(0, 8),
            itemId, storeId, locId, UUID.randomUUID(), UUID.randomUUID());

        return txId;
    }
}
