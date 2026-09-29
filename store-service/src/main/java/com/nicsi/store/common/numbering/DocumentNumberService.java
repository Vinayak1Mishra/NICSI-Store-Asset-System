package com.nicsi.store.common.numbering;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DocumentNumberService {

    private final JdbcTemplate jdbc;

    public DocumentNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Generates the next document number for the given type and prefix.
     * Format: PREFIX/FY/000001
     * 
     * This method MUST be called within an active transaction. The number
     * allocation is atomic and rollback-safe.
     * 
     * @param documentType e.g. "GRN", "ISSUE", "REQUISITION"
     * @param prefix e.g. "GRN", "ISS", "REQ"
     * @return formatted document number e.g. "GRN/2026-27/000001"
     */
    public String nextNumber(String documentType, String prefix) {
        return nextNumber(documentType, prefix, LocalDate.now());
    }
    
    /**
     * Generates the next document number for the given type, prefix and date.
     * Useful for backdated documents.
     */
    public String nextNumber(String documentType, String prefix, LocalDate referenceDate) {
        String fy = FinancialYearUtil.financialYear(referenceDate);
        
        Long nextNum = jdbc.queryForObject(
            "INSERT INTO store.document_sequence (id, document_type, financial_year, prefix, last_number, number_padding) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, 1, 6) " +
            "ON CONFLICT (document_type, financial_year) " +
            "DO UPDATE SET last_number = store.document_sequence.last_number + 1, " +
            "             updated_at = now() " +
            "RETURNING last_number",
            Long.class,
            documentType, fy, prefix
        );
        
        return prefix + "/" + fy + "/" + String.format("%06d", nextNum);
    }
}
