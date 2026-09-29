package com.nicsi.store.common.api;

import com.nicsi.store.common.security.CurrentUser;
import com.nicsi.store.common.security.CurrentUserHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/store")
public class SmokeController {

    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoami() {
        CurrentUser user = CurrentUserHolder.get();
        return ResponseEntity.ok(Map.of(
            "userId", user.userId(),
            "username", user.username(),
            "displayName", user.displayName(),
            "roles", user.roles(),
            "permissions", user.permissions(),
            "departmentId", user.departmentId() != null ? user.departmentId().toString() : "none"
        ));
    }

    @GetMapping("/ping")
    @PreAuthorize("hasAuthority('STORE_ADMIN')")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "timestamp", Instant.now().toString(),
            "service", "nicsi-store-service"
        ));
    }
}
