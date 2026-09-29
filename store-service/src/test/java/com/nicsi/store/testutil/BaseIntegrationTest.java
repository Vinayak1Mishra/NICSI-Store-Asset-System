package com.nicsi.store.testutil;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class BaseIntegrationTest {

    /**
     * The ONLY database integration tests are ever allowed to wipe.
     * application-dev.yml points at jdbc:postgresql://localhost:5432/nicsi_store (holds
     * smoke-test data) and must never be touched from a test.
     */
    public static final String EXPECTED_TEST_DATABASE = "nicsi_store_test";

    private static PostgreSQLContainer<?> postgres;
    private static boolean useDocker = false;

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                postgres = new PostgreSQLContainer<>("postgres:15")
                        .withDatabaseName("nicsi_store_test")
                        .withUsername("nicsi")
                        .withPassword("nicsi_dev");
                postgres.start();
                useDocker = true;
            }
        } catch (Throwable ignored) {
            useDocker = false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (useDocker && postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/nicsi_store_test");
            registry.add("spring.datasource.username", () -> "nicsi");
            registry.add("spring.datasource.password", () -> "nicsi_dev");
        }
        registry.add("spring.flyway.schemas", () -> "store,workflow,audit");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // The concurrency tests run N real threads, each needing its own connection.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    /**
     * Aborts unless the integration tests really are pointed at nicsi_store_test.
     * Call this at the top of every setUp that destroys data.
     */
    protected static void assertTestDatabase(JdbcTemplate jdbcTemplate, String testName) {
        String current;
        try {
            current = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        } catch (Exception e) {
            throw new IllegalStateException("[" + testName + "] Could not determine current_database(); refusing to wipe anything.", e);
        }
        if (!EXPECTED_TEST_DATABASE.equals(current)) {
            throw new IllegalStateException("[" + testName + "] REFUSING TO RUN: integration tests are connected to database '"
                    + current + "' but must be connected to '" + EXPECTED_TEST_DATABASE
                    + "'. Wiping this database would destroy real data. Fix the datasource URL before running.");
        }
    }

    /**
     * Children-before-parents truncation list for the inventory tests, derived from the
     * foreign-key graph in V2/V3/V4/V5.
     *
     * TRUNCATE is used instead of DELETE because store.stock_transaction carries
     * trg_stock_txn_immutable, a BEFORE UPDATE OR DELETE ... FOR EACH ROW trigger that
     * correctly refuses to let the ledger be erased. TRUNCATE bypasses row-level triggers,
     * so this is safe ONLY on the test database -- which assertTestDatabase() enforces above.
     *
     * The list is closed under foreign keys: every table that references a table in this
     * list is itself in this list, which is why no CASCADE is required or permitted.
     * Deliberately EXCLUDED, so they cannot be reached: audit.event, store.uom (22 seed
     * rows), every workflow.* table, and flyway_schema_history.
     */
    private static final String TRUNCATE_SQL =
            "TRUNCATE TABLE "
            + "store.asset_assignment, "                 // -> asset, storage_location, issue_header
            + "store.stock_adjustment_item, "            // -> stock_adjustment, item, storage_location, inventory_lot, asset
            + "store.return_item, "                       // -> return_header, asset, item, storage_location, store_site
            + "store.transfer_item, "                    // -> transfer_header, asset, item, inventory_lot, storage_location, store_site
            + "store.repair_ticket, "                     // -> asset
            + "store.support_contract, "                  // -> item, asset
            + "store.software_license_allocation, "       // -> software_license, asset
            + "store.physical_verification_item, "        // -> physical_verification, item, asset, storage_location, inventory_lot
            + "store.condemnation_item, "                // -> condemnation, asset
            + "store.disposal_item, "                    // -> disposal, asset
            + "store.inspection_item, "                  // -> inspection, grn_item, item
            + "store.stock_balance, "                    // -> item, store_site, storage_location, inventory_lot
            + "store.requisition_item, "                 // -> requisition
            + "store.stock_reservation, "                // -> requisition_item, item, store_site, storage_location, inventory_lot
            + "store.issue_item, "                       // -> requisition_item, stock_reservation, item, inventory_lot, storage_location, grn_item
            + "store.stock_transaction, "                // -> item, store_site, storage_location, inventory_lot, (self reversal_of_txn_id)
            + "store.asset, "                             // -> item, grn_item, issue_item, store_site, storage_location
            + "store.software_license, "                 // -> item
            + "store.physical_verification, "            // -> store_site
            + "store.condemnation, "                     // (no FKs)
            + "store.disposal, "                         // (no FKs)
            + "store.return_header, "                    // -> store_site
            + "store.transfer_header, "                 // -> store_site
            + "store.issue_header, "                     // -> requisition
            + "store.stock_adjustment, "                 // -> store_site
            + "store.grn_item, "                         // -> grn, purchase_order_item_ref, item
            + "store.inspection, "                       // -> grn, storage_location
            + "store.item_store_policy, "                // -> item, store_site, storage_location
            + "store.inventory_lot, "                    // -> item, store_site, grn_item
            + "store.purchase_order_item_ref, "          // -> purchase_order_ref, item
            + "store.item, "                             // -> item_category, item_subcategory, uom
            + "store.item_subcategory, "                 // -> item_category
            + "store.item_category, "                    // (no FKs)
            + "store.storage_location, "                 // -> store_site, (self parent_location_id)
            + "store.store_site, "                       // (no FKs)
            + "store.grn, "                              // -> store_site, purchase_order_ref
            + "store.purchase_order_ref, "               // (no FKs)
            + "store.document_sequence "                 // (no FKs)
            + "RESTART IDENTITY";

    /**
     * Asserts the test database, then truncates the inventory tables.
     * TRUNCATE bypasses the row-level immutability trigger on store.stock_transaction, so
     * this is safe only on nicsi_store_test.
     */
    protected static void truncateInventoryTables(JdbcTemplate jdbcTemplate, String testName) {
        assertTestDatabase(jdbcTemplate, testName);
        jdbcTemplate.execute(TRUNCATE_SQL);
    }
}
