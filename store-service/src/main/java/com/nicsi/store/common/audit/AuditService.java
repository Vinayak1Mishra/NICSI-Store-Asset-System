package com.nicsi.store.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nicsi.store.common.security.CurrentUser;
import com.nicsi.store.common.security.CurrentUserHolder;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class AuditService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an audit event. MUST be called within an active transaction.
     * The audit row will be committed or rolled back with the caller's transaction.
     */
    public void record(AuditEvent event) {
        CurrentUser user = null;
        try {
            user = CurrentUserHolder.get();
        } catch (Exception ignored) {
            // System operations may not have an authenticated user
        }

        String ipAddress = null;
        String userAgent = null;
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var request = attrs.getRequest();
                ipAddress = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception ignored) {}

        String correlationId = MDC.get("correlationId");

        jdbc.update(
            "INSERT INTO audit.event (id, event_time, actor_user_id, actor_username, actor_role, " +
            "module, action, entity_type, entity_id, entity_ref_no, " +
            "old_value, new_value, ip_address, user_agent, correlation_id, reason) " +
            "VALUES (gen_random_uuid(), now(), ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::inet, ?, ?, ?)",
            user != null ? user.userId() : null,
            user != null ? user.username() : "SYSTEM",
            user != null && user.roles() != null ? String.join(",", user.roles()) : null,
            event.module(),
            event.action(),
            event.entityType(),
            event.entityId(),
            event.entityRefNo(),
            toJson(event.oldValue()),
            toJson(event.newValue()),
            ipAddress,
            userAgent,
            correlationId,
            event.reason()
        );
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\": \"serialization_failed\"}";
        }
    }
}
