package com.nicsi.store.common.audit;

import java.util.UUID;

public record AuditEvent(
    String module,
    String action,
    String entityType,
    UUID entityId,
    String entityRefNo,
    Object oldValue,
    Object newValue,
    String reason
) {
    // Builder-style factory methods if needed
    public static AuditEvent of(String module, String action, String entityType, UUID entityId, String entityRefNo) {
        return new AuditEvent(module, action, entityType, entityId, entityRefNo, null, null, null);
    }
    
    public static AuditEvent ofChange(String module, String action, String entityType, UUID entityId, 
                                       String entityRefNo, Object oldValue, Object newValue) {
        return new AuditEvent(module, action, entityType, entityId, entityRefNo, oldValue, newValue, null);
    }
}
