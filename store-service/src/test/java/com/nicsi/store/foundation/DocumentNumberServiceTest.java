package com.nicsi.store.foundation;

import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DocumentNumberService.
 * Verifies sequential numbering, FY-awareness, rollback-safety,
 * and concurrent caller behavior.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DocumentNumberServiceTest extends BaseIntegrationTest {

    @Autowired
    private DocumentNumberService documentNumberService;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM store.document_sequence WHERE document_type IN ('SEQ_GRN', 'DIFF_GRN', 'DIFF_ISSUE', 'FY_MARCH', 'FY_APRIL', 'ROLLBACK_TEST', 'CONCURRENT')");
    }

    private String getFy(LocalDate date) {
        int year = date.getYear();
        if (date.getMonthValue() <= 3) {
            return (year - 1) + "-" + String.valueOf(year).substring(2);
        } else {
            return year + "-" + String.valueOf(year + 1).substring(2);
        }
    }

    @Test
    void testSequentialNumbering() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        String fy = getFy(LocalDate.now());

        // Each call in its own transaction so the sequence increments are committed
        String n1 = tx.execute(status -> documentNumberService.nextNumber("SEQ_GRN", "GRN"));
        String n2 = tx.execute(status -> documentNumberService.nextNumber("SEQ_GRN", "GRN"));
        String n3 = tx.execute(status -> documentNumberService.nextNumber("SEQ_GRN", "GRN"));

        assertThat(n1).isEqualTo("GRN/" + fy + "/000001");
        assertThat(n2).isEqualTo("GRN/" + fy + "/000002");
        assertThat(n3).isEqualTo("GRN/" + fy + "/000003");
    }

    @Test
    void testDifferentDocumentTypes() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        String fy = getFy(LocalDate.now());

        String grn = tx.execute(status -> documentNumberService.nextNumber("DIFF_GRN", "GRN"));
        String issue = tx.execute(status -> documentNumberService.nextNumber("DIFF_ISSUE", "ISSUE"));

        assertThat(grn).isEqualTo("GRN/" + fy + "/000001");
        assertThat(issue).isEqualTo("ISSUE/" + fy + "/000001");
    }

    @Test
    void testFinancialYearRollover() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        String marchNumber = tx.execute(status ->
            documentNumberService.nextNumber("FY_MARCH", "DT", LocalDate.of(2027, 3, 31)));
        String aprilNumber = tx.execute(status ->
            documentNumberService.nextNumber("FY_APRIL", "DT", LocalDate.of(2027, 4, 1)));

        assertThat(marchNumber).contains("2026-27");
        assertThat(aprilNumber).contains("2027-28");
    }

    @Test
    void testRollbackDoesNotBurnNumber() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        String fy = getFy(LocalDate.now());

        // Transaction that generates a number and then rolls back
        tx.execute(status -> {
            documentNumberService.nextNumber("ROLLBACK_TEST", "RB");
            status.setRollbackOnly();
            return null;
        });

        // The rolled-back number should not be "burned"
        String num = tx.execute(status -> documentNumberService.nextNumber("ROLLBACK_TEST", "RB"));
        assertThat(num).isEqualTo("RB/" + fy + "/000001");
    }

    @Test
    void testConcurrentCallers() throws InterruptedException {
        int threadCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<String> results = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // all threads start together
                    TransactionTemplate tx = new TransactionTemplate(txManager);
                    String num = tx.execute(status -> documentNumberService.nextNumber("CONCURRENT", "CT"));
                    results.add(num);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(results).hasSize(40);
        String fy = getFy(LocalDate.now());
        for (int i = 1; i <= 40; i++) {
            String expected = String.format("CT/%s/%06d", fy, i);
            assertThat(results).contains(expected);
        }
    }
}
