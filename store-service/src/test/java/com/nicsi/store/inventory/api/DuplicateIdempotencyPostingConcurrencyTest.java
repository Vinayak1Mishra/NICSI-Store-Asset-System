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
 * Phase 4 checklist item 2: duplicate idempotency key must replay, never post twice.
 *
 * 6 real threads post the same receipt with the same Idempotency-Key simultaneously. Exactly one
 * stock_transaction row may exist for that key, the balance must be credited exactly once, and
 * every thread must observe the same resulting transaction number.
 *
 * This targets the read-then-insert window in InventoryPostingService.post() (the
 * findByIdempotencyKey pre-check) against the UNIQUE constraint on stock_transaction.idempotency_key.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DuplicateIdempotencyPostingConcurrencyTest extends BaseIntegrationTest {

    private static final UUID ADMIN_ID = UUID.nameUUIDFromBytes("admin".getBytes());

    @Autowired
    private InventoryTestFixtures fixtures;
    @Autowired
    private InventoryPostingService postingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    @DisplayName("Same Idempotency-Key posted concurrently yields exactly one ledger row and one credit")
    void duplicateIdempotencyKeyPostsExactlyOnce() throws Exception {
        Ctx ctx = fixtures.newConsumable("Idempotency");
        UUID referenceId = UUID.randomUUID();
        String idempotencyKey = "IDEM-CONC-" + UUID.randomUUID();

        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<String> observedTransactionNos = Collections.synchronizedList(new ArrayList<>());
        List<String> dataIntegrityFailures = Collections.synchronizedList(new ArrayList<>());
        List<String> otherFailures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger succeeded = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    String txnNo = new TransactionTemplate(txManager).execute(status -> {
                        var txn = postingService.post(InventoryPostingRequest.builder()
                                .transactionType("RECEIPT")
                                .item(ctx.item())
                                .store(ctx.store())
                                .location(ctx.location())
                                .quantityIn(new BigDecimal("7.000"))
                                .unitCost(new BigDecimal("12.00"))
                                .referenceType("IDEMPOTENCY-TEST")
                                .referenceId(referenceId)
                                .idempotencyKey(idempotencyKey)
                                .postedBy(ADMIN_ID)
                                .build());
                        return txn == null ? null : txn.getTransactionNo();
                    });
                    if (txnNo != null) {
                        observedTransactionNos.add(txnNo);
                        succeeded.incrementAndGet();
                    }
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

        // A concurrent duplicate must be replayed or cleanly rejected -- never surface as a
        // raw constraint violation leaking out of the single inventory writer.
        assertThat(dataIntegrityFailures)
                .as("duplicate idempotency key must not leak a UNIQUE constraint violation")
                .isEmpty();
        assertThat(otherFailures).as("no unexpected failures").isEmpty();

        // Exactly one ledger row for the key, and the balance credited exactly once.
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_transaction WHERE idempotency_key = ?",
                Integer.class, idempotencyKey);
        assertThat(rowCount).as("exactly one ledger row for the idempotency key").isEqualTo(1);

        BigDecimal onHand = jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(onHand).as("balance credited exactly once, not once per thread").isEqualByComparingTo("7.000");

        // Every thread that returned a transaction must have seen the same one.
        assertThat(observedTransactionNos).isNotEmpty();
        assertThat(observedTransactionNos).as("all threads replay the same transaction").containsOnly(observedTransactionNos.get(0));
        assertThat(succeeded.get()).isEqualTo(threads);
    }
}
