package com.nicsi.store.dev;

import java.util.Set;
import java.util.UUID;

public record MockUser(
    UUID userId,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions,
    UUID departmentId,
    String departmentName
) {}
