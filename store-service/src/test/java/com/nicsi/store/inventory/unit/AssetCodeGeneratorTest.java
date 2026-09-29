package com.nicsi.store.inventory.unit;

import com.nicsi.store.asset.service.DefaultAssetCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AssetCodeGeneratorTest {

    @Test
    @DisplayName("Asset code format matches NICSI/<CATEGORY>/<YEAR>/<6-DIGIT_SEQ>")
    void testAssetCodeFormat() {
        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject(anyString(), eq(Long.class), eq("ASSET_IT"), eq("2026"), eq("NICSI/IT")))
                .thenReturn(125L);

        DefaultAssetCodeGenerator generator = new DefaultAssetCodeGenerator(mockJdbc);
        String code = generator.generateAssetCode("IT", LocalDate.of(2026, 4, 15));

        assertThat(code).isEqualTo("NICSI/IT/2026/000125");
    }

    @Test
    @DisplayName("Handles null or blank category with GEN default")
    void testDefaultCategoryFallback() {
        JdbcTemplate mockJdbc = Mockito.mock(JdbcTemplate.class);
        when(mockJdbc.queryForObject(anyString(), eq(Long.class), eq("ASSET_GEN"), eq("2026"), eq("NICSI/GEN")))
                .thenReturn(1L);

        DefaultAssetCodeGenerator generator = new DefaultAssetCodeGenerator(mockJdbc);
        String code = generator.generateAssetCode(null, LocalDate.of(2026, 9, 26));

        assertThat(code).isEqualTo("NICSI/GEN/2026/000001");
    }
}
