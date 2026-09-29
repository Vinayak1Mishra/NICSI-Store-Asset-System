package com.nicsi.store.foundation;

import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class FlywayMigrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testAllMigrationsApply() {
        // Implicitly tested if context loads and reaches here
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    void testTableCount() {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('store', 'workflow', 'audit')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(46);
    }

    @Test
    void testUomSeedData() {
        String sql = "SELECT COUNT(*) FROM store.uom WHERE uom_code IN ('NOS','EACH','PCS','SET','PAIR','BOX','PACK','REAM','ROLL','MTR','KM','KG','GM','LTR','ML','LICENSE','USER','DEVICE','MONTH','YEAR','LOT','KIT')";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        assertThat(count).isEqualTo(22);
    }

    @Test
    void testSpecificTablesExist() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema IN ('store', 'workflow', 'audit')";
        var tables = jdbcTemplate.queryForList(sql, String.class);
        
        assertThat(tables).contains(
            "stock_transaction",
            "stock_balance",
            "asset",
            "definition",
            "event"
        );
    }
}
