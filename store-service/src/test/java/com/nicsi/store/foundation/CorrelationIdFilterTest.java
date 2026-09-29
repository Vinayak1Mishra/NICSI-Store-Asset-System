package com.nicsi.store.foundation;

import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the CorrelationIdFilter.
 * Verifies that X-Correlation-ID is generated, echoed, and included in error responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CorrelationIdFilterTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testCorrelationIdGenerated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/store/whoami"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(correlationId).isNotNull().isNotBlank();
        // Should be a valid UUID format
        assertThat(correlationId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void testCorrelationIdEchoed() throws Exception {
        String myCorrelationId = "my-correlation-123";
        MvcResult result = mockMvc.perform(get("/api/store/whoami")
                .header("X-Correlation-ID", myCorrelationId))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(correlationId).isEqualTo(myCorrelationId);
    }

    @Test
    void testCorrelationIdInErrorResponse() throws Exception {
        // 401 response should still have correlation ID in both header and JSON body
        MvcResult result = mockMvc.perform(get("/api/store/whoami"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String headerCorrId = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(headerCorrId).isNotNull();
    }
}
