package com.nicsi.store.asset.service;

import java.time.LocalDate;

public interface AssetCodeGenerator {
    /**
     * Generates a unique human-readable asset code.
     * Default pattern: NICSI/<category-code>/<year>/<6-digit-sequence>
     */
    String generateAssetCode(String categoryCode, LocalDate referenceDate);
}
