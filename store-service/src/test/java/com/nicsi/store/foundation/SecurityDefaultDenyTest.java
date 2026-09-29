package com.nicsi.store.foundation;

import com.nicsi.store.testutil.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the default-deny posture: anyRequest() is authenticated, so a path outside
 * /api/store/** is unreachable without a token. Before this was fixed, SecurityConfig ended
 * with anyRequest().permitAll(), which made any path that did not match /api/store/**
 * anonymously reachable -- notably /api/inventory/** and /api/assets/**, the exact paths
 * the PDF documents but the app has not built.
 *
 * The distinction that matters: these paths must return 401, NOT 404. A 404 would mean the
 * request reached the dispatcher and the security filter let an anonymous caller through to
 * a routing miss, i.e. the endpoint is public-but-absent. A 401 proves the filter rejected
 * it first, so adding a controller on such a path later cannot silently expose it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityDefaultDenyTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Unauthenticated GET /api/inventory/balance returns 401, not 404")
    void inventoryBalanceWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/inventory/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated GET /api/assets/{code} returns 401, not 404")
    void assetByCodeWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/assets/ASSET-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated GET on a completely random path returns 401, not 404")
    void randomPathWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/this/path/does/not/exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated root path returns 401, not 404")
    void rootPathWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("actuator/health stays public after the default-deny change")
    void actuatorHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("dev token endpoint stays public after the default-deny change")
    void devTokenEndpointRemainsPublic() throws Exception {
        mockMvc.perform(post("/api/store/dev/token").param("username", "admin"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/error is not blocked by the security filter")
    void errorEndpointIsNotBlockedBySecurity() throws Exception {
        // Spring Security's default dispatcher-types include ERROR, and JwtAuthenticationFilter is a
        // OncePerRequestFilter that does not re-run on an ERROR dispatch. So on the error pass the
        // SecurityContext is empty; if /error were authenticated, a tokenless request to any unmapped
        // path could be bounced 401 by the error dispatch itself. Permitting /error keeps the 401
        // stable and lets the real routing 404 be reported on authenticated requests.
        //
        // Asserted as "not 401 and not 403" rather than a specific code: the status depends on the
        // error attributes the container sets, and MockMvc does not reproduce a container ERROR
        // dispatch. What this test actually pins down is that /error is not rejected by security.
        mockMvc.perform(get("/error"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value()));
    }
}
