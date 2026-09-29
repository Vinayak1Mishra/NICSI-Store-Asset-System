package com.nicsi.store.asset.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class DefaultAssetCodeGenerator implements AssetCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public DefaultAssetCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String generateAssetCode(String categoryCode, LocalDate referenceDate) {
        String cleanCategory = (categoryCode != null && !categoryCode.isBlank()) ? categoryCode.trim().toUpperCase() : "GEN";
        LocalDate date = referenceDate != null ? referenceDate : LocalDate.now();
        String year = String.valueOf(date.getYear());
        String docType = "ASSET_" + cleanCategory;

        Long nextNum = jdbcTemplate.queryForObject(
                "INSERT INTO store.document_sequence (id, document_type, financial_year, prefix, last_number, number_padding) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, 1, 6) " +
                "ON CONFLICT (document_type, financial_year) " +
                "DO UPDATE SET last_number = store.document_sequence.last_number + 1, " +
                "             updated_at = now() " +
                "RETURNING last_number",
                Long.class,
                docType, year, "NICSI/" + cleanCategory
        );

        return "NICSI/" + cleanCategory + "/" + year + "/" + String.format("%06d", nextNum);
    }
}
