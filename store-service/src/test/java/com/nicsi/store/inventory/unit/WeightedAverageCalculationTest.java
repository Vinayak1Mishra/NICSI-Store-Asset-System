package com.nicsi.store.inventory.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedAverageCalculationTest {

    private BigDecimal calculateNewAverage(
            BigDecimal currentQty, BigDecimal currentAvgCost,
            BigDecimal receiptQty, BigDecimal receiptRate
    ) {
        BigDecimal newQty = currentQty.add(receiptQty);
        if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
            return receiptRate;
        }
        BigDecimal currentTotal = currentQty.multiply(currentAvgCost);
        BigDecimal receiptTotal = receiptQty.multiply(receiptRate);
        return currentTotal.add(receiptTotal).divide(newQty, 4, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("Initial receipt sets average unit cost directly")
    void testInitialReceiptSetsAverageCost() {
        BigDecimal initialQty = BigDecimal.ZERO;
        BigDecimal initialAvg = BigDecimal.ZERO;
        BigDecimal receiptQty = new BigDecimal("10.000");
        BigDecimal receiptRate = new BigDecimal("100.0000");

        BigDecimal newAvg = calculateNewAverage(initialQty, initialAvg, receiptQty, receiptRate);
        assertThat(newAvg).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("Subsequent receipt calculates weighted average unit cost correctly")
    void testSubsequentReceiptWeightedAverage() {
        // 10 units @ 100.00 = 1000
        BigDecimal currentQty = new BigDecimal("10.000");
        BigDecimal currentAvg = new BigDecimal("100.0000");

        // Receive 10 units @ 120.00 = 1200
        BigDecimal receiptQty = new BigDecimal("10.000");
        BigDecimal receiptRate = new BigDecimal("120.0000");

        // Total 20 units, Total value 2200 -> Average = 110.0000
        BigDecimal newAvg = calculateNewAverage(currentQty, currentAvg, receiptQty, receiptRate);
        assertThat(newAvg).isEqualByComparingTo("110.0000");
    }

    @Test
    @DisplayName("Unequal receipt quantities calculate proper weighted average")
    void testUnequalReceiptQuantities() {
        // 5 units @ 50.00 = 250
        BigDecimal currentQty = new BigDecimal("5.000");
        BigDecimal currentAvg = new BigDecimal("50.0000");

        // Receive 15 units @ 70.00 = 1050
        // Total = 1300 / 20 = 65.0000
        BigDecimal receiptQty = new BigDecimal("15.000");
        BigDecimal receiptRate = new BigDecimal("70.0000");

        BigDecimal newAvg = calculateNewAverage(currentQty, currentAvg, receiptQty, receiptRate);
        assertThat(newAvg).isEqualByComparingTo("65.0000");
    }

    @Test
    @DisplayName("Fractional rates and quantities calculate to 4 decimal precision")
    void testFractionalRounding() {
        BigDecimal currentQty = new BigDecimal("7.333");
        BigDecimal currentAvg = new BigDecimal("33.3333");

        BigDecimal receiptQty = new BigDecimal("12.667");
        BigDecimal receiptRate = new BigDecimal("45.5000");

        BigDecimal newAvg = calculateNewAverage(currentQty, currentAvg, receiptQty, receiptRate);
        // (7.333 * 33.3333) = 244.4330891
        // (12.667 * 45.5000) = 576.3485
        // Total = 820.7815891 / 20.000 = 41.039079 -> 41.0391
        assertThat(newAvg).isEqualByComparingTo("41.0391");
    }
}
