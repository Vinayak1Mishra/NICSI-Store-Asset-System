package com.nicsi.store.inventory.api;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
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
import java.util.ArrayList;
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
 * Phase 4 checklist item 1: single inventory writer, and item 12: no oversell under concurrency.
 *
 * 8 real threads each try to issue 3 units against a balance of 10. Exactly 3 must succeed
 * (3 + 3 + 3 = 9, a 4th would need 12). The 5 losers must all be rejected with
 * INSUFFICIENT_STOCK, and the ledger must contain exactly 3 ISSUE rows.
 *
 * Isolation: option A. This test creates its own item / store / location with a random tag and
 * never wipes a table, so it cannot disturb any other test and is safe to run repeatedly.
 * Committed data, real threads, separate pooled connections, CountDownLatch start gate.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StockIssuanceConcurrencyTest extends BaseIntegrationTest {

    private static final UUID ADMIN_ID = UUID.nameUUIDFromBytes("admin".getBytes());

    @Autowired
    private InventoryTestFixtures fixtures;
    @Autowired
    private InventoryPostingService postingService;
    @Autowired
    private StockBalanceRepository stockBalanceRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    @DisplayName("Concurrent issues cannot oversell: pessimistic lock serialises the single writer")
    void concurrentIssuesCannotOversell() throws Exception {
        Ctx ctx = fixtures.newConsumable("Issuance");
        fixtures.receive(ctx, "10", "5.00", ADMIN_ID);

        int threads = 8;
        BigDecimal each = new BigDecimal("3.000");

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<String> unexpectedMessages = java.util.Collections.synchronizedList(new ArrayList<>());

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

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
                    // Each thread runs in its own transaction on its own pooled connection.
                    new TransactionTemplate(txManager).executeWithoutResult(status -> postingService.post(
                            InventoryPostingRequest.builder()
                                    .transactionType("ISSUE")
                                    .item(ctx.item())
                                    .store(ctx.store())
                                    .location(ctx.location())
                                    .quantityOut(each)
                                    .referenceType("CONCURRENCY-ISSUE")
                                    .referenceId(UUID.randomUUID())
                                    .postedBy(ADMIN_ID)
                                    .build()));
                    succeeded.incrementAndGet();
                } catch (BusinessException be) {
                    if ("INSUFFICIENT_STOCK".equals(be.getErrorCode())) {
                        insufficient.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                        unexpectedMessages.add("thread " + n + ": " + be.getErrorCode() + " " + be.getMessage());
                    }
                } catch (RuntimeException e) {
                    unexpected.incrementAndGet();
                    unexpectedMessages.add("thread " + n + ": " + e);
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

        assertThat(unexpectedMessages).as("no unexpected failures").isEmpty();
        assertThat(unexpected.get()).as("no unexpected failures").isZero();
        assertThat(succeeded.get()).as("only 3 issues of 3 fit into an on-hand of 10").isEqualTo(3);
        assertThat(insufficient.get()).as("the other 5 are rejected for insufficient stock").isEqualTo(5);

        // On-hand must be exactly 10 - 9 = 1 and must never have gone negative.
        StockBalance balance = stockBalanceRepository.findByDimensions(
                ctx.item().getId(), ctx.store().getId(), ctx.location().getId(), null).orElseThrow();
        assertThat(balance.getOnHandQty()).isEqualByComparingTo("1.000");
        assertThat(balance.getOnHandQty()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Immutable ledger must contain exactly one receipt and exactly three issues for this item.
        assertThat(countLedgerRows(ctx, "RECEIPT")).as("one receipt row").isEqualTo(1);
        assertThat(countLedgerRows(ctx, "ISSUE")).as("exactly three issue rows").isEqualTo(3);

        // Ledger net must equal the balance: the two can never drift apart.
        BigDecimal ledgerNet = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_in),0) - COALESCE(SUM(quantity_out),0) FROM store.stock_transaction "
                        + "WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
        assertThat(ledgerNet).isEqualByComparingTo(balance.getOnHandQty());
    }

    private int countLedgerRows(Ctx ctx, String type) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM store.stock_transaction "
                        + "WHERE item_id = ? AND store_id = ? AND location_id = ? AND transaction_type = ?",
                Integer.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId(), type);
        return n == null ? 0 : n;
    }
}
