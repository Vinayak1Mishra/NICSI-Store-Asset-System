package com.nicsi.store.inventory.api;

import com.nicsi.store.inventory.service.InventoryPostingRequest;
import com.nicsi.store.inventory.service.InventoryPostingService;
import com.nicsi.store.testutil.BaseIntegrationTest;
import com.nicsi.store.testutil.InventoryTestFixtures;
import com.nicsi.store.testutil.InventoryTestFixtures.Ctx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the first-write balance race in InventoryPostingService.
 *
 * Before the fix, findByDimensionsForUpdate is SELECT ... FOR UPDATE, which locks nothing when no
 * stock_balance row exists yet. Eight concurrent first-ever receipts into the same brand-new
 * location therefore all missed, all built a new StockBalance, and five of them died on
 * uq_stock_balance_dims with a DataIntegrityViolationException that reached the caller (HTTP 500).
 *
 * The pg_advisory_xact_lock taken before the row lock now serialises the bootstrap, so all eight
 * must succeed, exactly one balance row may exist, and the weighted average must be the mean of
 * eight identical-cost receipts.
 *
 * Isolation: option A. Unique item / store / location codes, no table wipes, every assertion
 * scoped to this test's own ids. Committed data, real threads, separate connections, latch gate.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StockBalanceBootstrapConcurrencyTest extends BaseIntegrationTest {

    private static final UUID ADMIN_ID = UUID.nameUUIDFromBytes("admin".getBytes());
    private static final BigDecimal QTY = new BigDecimal("5.000");
    private static final BigDecimal COST = new BigDecimal("25.00");

    @Autowired
    private InventoryTestFixtures fixtures;
    @Autowired
    private InventoryPostingService postingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    @DisplayName("Eight concurrent first postings into a new location all succeed with one balance row")
    void concurrentFirstPostingsBootstrapOneBalance() throws Exception {
        Ctx ctx = fixtures.newConsumable("Bootstrap");

        // Precondition: this location genuinely has no stock_balance row yet.
        Integer preCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                Integer.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(preCount).as("no balance row exists before the race").isZero();

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        AtomicInteger succeeded = new AtomicInteger();
        List<String> dataIntegrityFailures = Collections.synchronizedList(new ArrayList<>());
        List<String> otherFailures = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    // Distinct idempotency keys: this test targets the balance bootstrap race,
                    // not idempotency replay.
                    new TransactionTemplate(txManager).executeWithoutResult(status -> postingService.post(
                            InventoryPostingRequest.builder()
                                    .transactionType("RECEIPT")
                                    .item(ctx.item())
                                    .store(ctx.store())
                                    .location(ctx.location())
                                    .quantityIn(QTY)
                                    .unitCost(COST)
                                    .referenceType("BOOTSTRAP-TEST")
                                    .referenceId(UUID.randomUUID())
                                    .idempotencyKey("IDEM-BOOT-" + n + "-" + UUID.randomUUID())
                                    .postedBy(ADMIN_ID)
                                    .build()));
                    succeeded.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    dataIntegrityFailures.add(String.valueOf(e.getMostSpecificCause().getMessage()));
                } catch (RuntimeException e) {
                    otherFailures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }));
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).as("all threads parked before the gate").isTrue();
        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("pool terminated").isTrue();

        // The whole point: no constraint violation escapes the single inventory writer.
        assertThat(dataIntegrityFailures)
                .as("no DataIntegrityViolationException may reach the caller")
                .isEmpty();
        assertThat(otherFailures).as("no unexpected failures").isEmpty();
        assertThat(succeeded.get()).as("all eight first postings succeed").isEqualTo(threads);

        // Exactly one balance row for the dimension tuple.
        Integer balanceRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                Integer.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(balanceRows).as("exactly one balance row for the dimensions").isEqualTo(1);

        BigDecimal onHand = jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        BigDecimal avgCost = jdbcTemplate.queryForObject(
                "SELECT avg_unit_cost FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());

        assertThat(onHand).as("8 x 5 = 40 on hand").isEqualByComparingTo("40.000");
        // Every receipt is the same cost, so the weighted average must be exactly that cost.
        assertThat(avgCost).as("weighted average of identical-cost receipts").isEqualByComparingTo("25.0000");

        // Exactly eight immutable ledger rows.
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_transaction WHERE item_id = ? AND store_id = ? AND location_id = ?",
                Integer.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(ledgerRows).as("exactly eight ledger rows").isEqualTo(threads);

        // Ledger net must equal the balance: the two can never drift apart.
        BigDecimal ledgerNet = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_in),0) - COALESCE(SUM(quantity_out),0) FROM store.stock_transaction "
                        + "WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(ledgerNet).isEqualByComparingTo(onHand);
    }
}
