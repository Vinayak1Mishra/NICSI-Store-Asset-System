package com.nicsi.store.foundation;

import com.nicsi.store.common.numbering.FinancialYearUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialYearUtilTest {

    @Test
    void testSeptember2026() {
        assertThat(FinancialYearUtil.getFinancialYear(LocalDate.of(2026, 9, 24))).isEqualTo("2026-27");
    }

    @Test
    void testMarch2027() {
        assertThat(FinancialYearUtil.getFinancialYear(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
    }

    @Test
    void testApril2027() {
        assertThat(FinancialYearUtil.getFinancialYear(LocalDate.of(2027, 4, 1))).isEqualTo("2027-28");
    }

    @Test
    void testJanuary2028() {
        assertThat(FinancialYearUtil.getFinancialYear(LocalDate.of(2028, 1, 15))).isEqualTo("2027-28");
    }

    @Test
    void testApril1st() {
        assertThat(FinancialYearUtil.getFinancialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
    }

    @Test
    void testMarch31st() {
        assertThat(FinancialYearUtil.getFinancialYear(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
    }
}
