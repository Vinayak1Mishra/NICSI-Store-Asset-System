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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
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
 * Phase 4 checklist item 1: the single inventory writer must not deadlock under concurrency.
 *
 * Two shapes are exercised against the same three balance dimensions:
 *
 *  1. One dimension per thread -- the shape every real caller uses today (a GRN line, an adjustment
 *     line). Each thread takes a single advisory lock, so the threads queue rather than conflict.
 *  2. Three dimensions per thread in ROTATED order (A,B,C / B,C,A / C,A,B) -- the adversarial shape
 *     that would deadlock if advisory locks were acquired in call order. This asserts the ordering
 *     contract holds, or reports exactly which ordering breaks.
 *
 * Isolation: option A. Unique item/store/location codes per dimension, no table wipes, all
 * assertions scoped to this test's own ids. Committed data, real threads, separate connections.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DeadlockPreventionTest extends BaseIntegrationTest {

    private static final UUID ADMIN_ID = UUID.nameUUIDFromBytes("admin".getBytes());
    private static final BigDecimal QTY = new BigDecimal("1.000");

    @Autowired
    private InventoryTestFixtures fixtures;
    @Autowired
    private InventoryPostingService postingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    @DisplayName("One dimension per thread: concurrent postings serialise without deadlock")
    void oneDimensionPerThreadNeverDeadlocks() throws Exception {
        List<Ctx> dims = threeBootstrappedDimensions("DeadlockSingle");

        AtomicInteger ok = new AtomicInteger();
        List<String> lockFailures = Collections.synchronizedList(new ArrayList<>());
        List<String> otherFailures = Collections.synchronizedList(new ArrayList<>());

        int threads = dims.size() * 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final Ctx ctx = dims.get(i % dims.size());
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int round = 0; round < 3; round++) {
                    try {
                        post(ctx);
                        ok.incrementAndGet();
                    } catch (RuntimeException e) {
                        classify(e, lockFailures, otherFailures);
                    }
                }
            }));
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("pool terminated").isTrue();

        assertThat(lockFailures).as("no deadlock or lock timeout").isEmpty();
        assertThat(otherFailures).as("no unexpected failures").isEmpty();
        assertThat(ok.get()).isEqualTo(threads * 3);

        // Ledger must match the balance for every dimension.
        for (Ctx ctx : dims) {
            assertLedgerMatchesBalance(ctx);
        }
    }

    @Test
    @DisplayName("Rotated multi-dimension order within one transaction: ordering contract holds")
    void rotatedMultiDimensionOrderNeverDeadlocks() throws Exception {
        List<Ctx> dims = threeBootstrappedDimensions("DeadlockRotate");

        AtomicInteger ok = new AtomicInteger();
        List<String> lockFailures = Collections.synchronizedList(new ArrayList<>());
        List<String> otherFailures = Collections.synchronizedList(new ArrayList<>());

        int threads = dims.size();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            // Each thread walks all three dimensions starting from a different one: the exact
            // lock-order inversion that produces a deadlock if locks are taken in call order.
            final List<Ctx> rotation = rotate(dims, i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int round = 0; round < 3; round++) {
                    try {
                        // All three lines in ONE transaction, so the advisory locks are held
                        // simultaneously across the whole posting.
                        new TransactionTemplate(txManager).executeWithoutResult(status -> {
                            for (Ctx ctx : rotation) {
                                post(ctx);
                            }
                        });
                        ok.incrementAndGet();
                    } catch (RuntimeException e) {
                        classify(e, lockFailures, otherFailures);
                    }
                }
            }));
        }

        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("pool terminated").isTrue();

        assertThat(lockFailures).as("no deadlock or lock timeout across rotated orderings").isEmpty();
        assertThat(otherFailures).as("no unexpected failures").isEmpty();
        assertThat(ok.get()).isEqualTo(threads * 3);

        for (Ctx ctx : dims) {
            assertLedgerMatchesBalance(ctx);
        }
    }

    private List<Ctx> threeBootstrappedDimensions(String label) {
        List<Ctx> dims = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Ctx ctx = fixtures.newConsumable(label + "-" + i);
            // Pre-create the balance so this test measures lock ordering, not balance bootstrap.
            fixtures.receive(ctx, "100", "10.00", ADMIN_ID);
            dims.add(ctx);
        }
        return dims;
    }

    private static List<Ctx> rotate(List<Ctx> in, int by) {
        List<Ctx> out = new ArrayList<>(in);
        Collections.rotate(out, by);
        return out;
    }

    private void post(Ctx ctx) {
        postingService.post(InventoryPostingRequest.builder()
                .transactionType("ISSUE")
                .item(ctx.item())
                .store(ctx.store())
                .location(ctx.location())
                .quantityOut(QTY)
                .referenceType("DEADLOCK-TEST")
                .referenceId(UUID.randomUUID())
                .postedBy(ADMIN_ID)
                .build());
    }

    /** SQLSTATE class 40 -- 40001 serialization_failure, 40P01 deadlock_detected, 55P03 lock_not_available. */
    private void classify(RuntimeException e, List<String> lockFailures, List<String> otherFailures) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("40")) {
                lockFailures.add(sql.getSQLState() + ": " + sql.getMessage());
                return;
            }
            t = t.getCause();
        }
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("deadlock")) {
            lockFailures.add("deadlock: " + e.getMessage());
            return;
        }
        otherFailures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private void assertLedgerMatchesBalance(Ctx ctx) {
        BigDecimal onHand = jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        BigDecimal ledgerNet = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_in),0) - COALESCE(SUM(quantity_out),0) FROM store.stock_transaction "
                        + "WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(ledgerNet).as("ledger net == balance for " + ctx.item().getItemCode()).isEqualByComparingTo(onHand);
    }
}
