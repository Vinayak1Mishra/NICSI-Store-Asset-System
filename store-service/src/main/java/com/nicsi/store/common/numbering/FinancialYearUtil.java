package com.nicsi.store.common.numbering;

import java.time.LocalDate;

public final class FinancialYearUtil {
    private FinancialYearUtil() {}
    
    /**
     * Returns the financial year string for the given date.
     * FY runs April 1 to March 31.
     * Example: Sept 2026 -> "2026-27", March 2027 -> "2026-27", April 2027 -> "2027-28"
     */
    public static String financialYear(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int startYear = month >= 4 ? year : year - 1;
        int endYear = startYear + 1;
        return startYear + "-" + String.valueOf(endYear).substring(2);
    }

    /**
     * Alias for financialYear.
     */
    public static String getFinancialYear(LocalDate date) {
        return financialYear(date);
    }
    
    /**
     * Returns the financial year string for today.
     */
    public static String currentFinancialYear() {
        return financialYear(LocalDate.now());
    }
}
