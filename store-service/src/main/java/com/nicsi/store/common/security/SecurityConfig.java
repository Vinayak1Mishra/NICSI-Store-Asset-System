package com.nicsi.store.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

/**
 * Spring Security configuration.
 * - Stateless JWT resource server
 * - CSRF disabled (API only)
 * - CORS allowed from localhost:3000 (dev)
 * - Dev endpoints (/api/store/dev/**) permitted without auth
 * - All other /api/store/** require authentication
 * - Default-deny: anyRequest() is authenticated, so unmapped or newly added
 *   paths outside /api/store/** are not publicly reachable
 * - Method-level security enabled for @PreAuthorize
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:3001"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                config.setAllowedHeaders(List.of("*"));
                config.setExposedHeaders(List.of("X-Correlation-ID"));
                config.setAllowCredentials(true);
                return config;
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/store/dev/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Unmapped paths forward here on an ERROR dispatch. JwtAuthenticationFilter is a
                // OncePerRequestFilter and does not re-run on ERROR, so without this the SecurityContext
                // is empty on the second pass and authenticated callers get 401 instead of 404.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/store/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    ErrorResponse errorResponse = ErrorResponse.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        "UNAUTHORIZED",
                        "Authentication is required to access this resource",
                        null,
                        MDC.get("correlationId"),
                        request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    ErrorResponse errorResponse = ErrorResponse.of(
                        HttpStatus.FORBIDDEN.value(),
                        HttpStatus.FORBIDDEN.getReasonPhrase(),
                        "FORBIDDEN",
                        "You do not have permission to access this resource",
                        null,
                        MDC.get("correlationId"),
                        request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
            );

        return http.build();
    }
}
