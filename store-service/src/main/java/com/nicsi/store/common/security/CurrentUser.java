package com.nicsi.store.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Record representing the currently authenticated user's information.
 * Extracted from the JWT token and stored in the SecurityContext.
 */
public record CurrentUser(
    UUID userId,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions,
    UUID departmentId,
    String ipAddress
) {
    /**
     * Checks if the user has a specific permission.
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * Checks if the user has a specific role.
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
