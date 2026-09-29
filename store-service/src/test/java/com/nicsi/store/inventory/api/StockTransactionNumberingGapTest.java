package com.nicsi.store.inventory.api;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.FinancialYearUtil;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Documents the STOCK_TXN numbering behaviour created by the D4 deadlock fix. This test pins the
 * CURRENT behaviour; it does not fix anything.
 *
 * Background. Since D4, nextNumber() runs BEFORE the balance advisory lock, so a number is
 * allocated before it is known whether the line will really post. DocumentNumberService.nextNumber
 * is not @Transactional and does not use REQUIRES_NEW, so it joins the caller's transaction and its
 * increment is rolled back with it on failure.
 *
 * post() has two idempotency checks: a FAST check at the very top, and a SECOND check after the
 * advisory lock. That gives three distinct outcomes, all covered below:
 *
 *  1. SEQUENTIAL replay -- the first post has already committed, so the fast check finds the row
 *     and returns BEFORE nextNumber runs. No number is allocated. NO GAP.
 *  2. FAILED posting -- nextNumber ran, but the caller rolls back, taking the increment with it.
 *     NO GAP.
 *  3. CONCURRENT replay -- the loser of a race passed the fast check (row not yet visible), then
 *     allocated a number, then the second check found the winner's committed row and returned. The
 *     transaction commits, so THAT NUMBER IS BURNED. GAP -- one per racing duplicate that got past
 *     the fast check.
 *
 * The gap is therefore narrow and bounded: it needs a genuine concurrent duplicate, it never
 * affects correctness (no duplicate ledger row, balance credited once), and it only leaves holes in
 * a monotonic, non-gapless document sequence.
 *
 * Isolation: option A, no table wipes.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StockTransactionNumberingGapTest extends BaseIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private InventoryTestFixtures fixtures;
    @Autowired
    private InventoryPostingService postingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Sequential replay: fast idempotency check returns before nextNumber, so no gap")
    void sequentialReplayBurnsNoNumber() {
        UUID adminId = UUID.nameUUIDFromBytes("admin".getBytes());
        Ctx ctx = fixtures.newConsumable("NumberingSeq");
        String fy = fy();

        long before = lastNumber("STOCK_TXN", fy);
        String key = "IDEM-GAP-SEQ-" + UUID.randomUUID();

        postingService.post(receipt(ctx, key, adminId));
        long afterFirst = lastNumber("STOCK_TXN", fy);
        assertThat(afterFirst).as("one number allocated by the real posting").isEqualTo(before + 1);
        assertThat(ledgerRows(ctx)).isEqualTo(1L);

        postingService.post(receipt(ctx, key, adminId));
        long afterReplay = lastNumber("STOCK_TXN", fy);

        // The fast path returned the existing row, so nextNumber was never reached.
        assertThat(ledgerRows(ctx)).as("nothing duplicated").isEqualTo(1L);
        assertThat(afterReplay)
                .as("sequential replay burns NO number -- the fast idempotency check short-circuits first")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("Failed posting: the sequence increment rolls back with the caller, so no gap")
    void failedPostingBurnsNoNumber() {
        UUID adminId = UUID.nameUUIDFromBytes("admin".getBytes());
        Ctx ctx = fixtures.newConsumable("NumberingFail");
        fixtures.receive(ctx, "5", "10.00", adminId);

        long before = lastNumber("STOCK_TXN", fy());

        assertThatThrownBy(() -> postingService.post(InventoryPostingRequest.builder()
                .transactionType("ISSUE")
                .item(ctx.item())
                .store(ctx.store())
                .location(ctx.location())
                .quantityOut(new BigDecimal("50.000"))
                .referenceType("NUMBERING-TEST")
                .referenceId(UUID.randomUUID())
                .postedBy(adminId)
                .build()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("INSUFFICIENT_STOCK");

        // nextNumber ran before the failure, but its increment shares the caller's transaction, so
        // the rollback takes it with it.
        assertThat(lastNumber("STOCK_TXN", fy()))
                .as("a failed posting rolls the sequence back -- no gap")
                .isEqualTo(before);
        assertThat(ledgerRows(ctx)).as("only the opening receipt remains").isEqualTo(1L);
    }

    @Test
    @DisplayName("Concurrent replay: losers that got past the fast check each burn one number")
    void concurrentReplayCanBurnNumbers() throws Exception {
        UUID adminId = UUID.nameUUIDFromBytes("admin".getBytes());
        Ctx ctx = fixtures.newConsumable("NumberingRace");
        String fy = fy();
        long before = lastNumber("STOCK_TXN", fy);
        String key = "IDEM-GAP-RACE-" + UUID.randomUUID();

        AtomicInteger failures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    postingService.post(receipt(ctx, key, adminId));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(failures.get()).as("no thread may fail").isZero();

        long burned = lastNumber("STOCK_TXN", fy) - before;
        long rows = ledgerRows(ctx);

        // Correctness is unaffected: exactly one ledger row, one credit, no duplication.
        assertThat(rows).as("exactly one ledger row despite 6 concurrent same-key posts").isEqualTo(1L);
        assertThat(onHand(ctx)).as("credited exactly once").isEqualByComparingTo("10.000");

        // At most one number per thread: a thread that hit the fast path burns nothing, and a
        // thread that allocated one burns exactly one. The exact count is timing-dependent --
        // it depends on how many threads passed the fast check before the winner committed -- so
        // this asserts the bound, not a fixed number. Any value in this range means "gaps occurred
        // equal to burned - 1", because the one surviving number belongs to the real posting.
        assertThat(burned).as("at most one number per racing thread").isBetween(1L, (long) THREADS);
    }

    private InventoryPostingRequest receipt(Ctx ctx, String key, UUID adminId) {
        return InventoryPostingRequest.builder()
                .transactionType("RECEIPT")
                .item(ctx.item())
                .store(ctx.store())
                .location(ctx.location())
                .quantityIn(new BigDecimal("10.000"))
                .unitCost(new BigDecimal("10.00"))
                .referenceType("NUMBERING-TEST")
                .referenceId(UUID.randomUUID())
                .idempotencyKey(key)
                .postedBy(adminId)
                .build();
    }

    private static String fy() {
        return FinancialYearUtil.financialYear(LocalDate.now());
    }

    private long lastNumber(String documentType, String financialYear) {
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT last_number FROM store.document_sequence WHERE document_type = ? AND financial_year = ?",
                Long.class, documentType, financialYear);
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    private long ledgerRows(Ctx ctx) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM store.stock_transaction WHERE item_id = ? AND store_id = ? AND location_id = ?",
                Long.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
    }

    private BigDecimal onHand(Ctx ctx) {
        return jdbcTemplate.queryForObject(
                "SELECT on_hand_qty FROM store.stock_balance WHERE item_id = ? AND store_id = ? AND location_id = ?",
                BigDecimal.class, ctx.item().getId(), ctx.store().getId(), ctx.location().getId());
    }
}
