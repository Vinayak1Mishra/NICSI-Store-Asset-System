package com.nicsi.store.dev;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * DEV-ONLY controller that generates JWT tokens for mock users.
 * This controller is active only under the "dev" profile and must be
 * removed or disabled before production deployment.
 */
@RestController
@Profile(DevProfile.DEV)
@RequestMapping("/api/store/dev")
public class DevTokenController {

    private final MockUsers mockUsers;
    private final String jwtSecret;
    private final long tokenExpiryHours;

    public DevTokenController(
            MockUsers mockUsers,
            @Value("${nicsi.security.jwt-secret}") String jwtSecret,
            @Value("${nicsi.security.token-expiry-hours:24}") long tokenExpiryHours) {
        this.mockUsers = mockUsers;
        this.jwtSecret = jwtSecret;
        this.tokenExpiryHours = tokenExpiryHours;
    }

    /**
     * Generate a JWT for the specified mock user.
     *
     * @param username one of the 11 mock usernames (e.g. "store.operator", "admin")
     * @return JSON with token, expiry info, and user details
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> generateToken(@RequestParam String username) {
        MockUser user = mockUsers.getByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Unknown mock user: " + username,
                "availableUsers", mockUsers.getAllUsersMap().keySet()
            ));
        }

        Instant now = Instant.now();
        Instant exp = now.plus(tokenExpiryHours, ChronoUnit.HOURS);

        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(user.userId().toString())
                .claim("username", user.username())
                .claim("name", user.displayName())
                .claim("roles", user.roles().stream().sorted().toList())
                .claim("permissions", user.permissions().stream().sorted().toList())
                .claim("department_id", user.departmentId() != null ? user.departmentId().toString() : null)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();

        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresIn", tokenExpiryHours * 3600,
                "user", Map.of(
                    "userId", user.userId().toString(),
                    "username", user.username(),
                    "displayName", user.displayName(),
                    "roles", user.roles(),
                    "permissions", user.permissions(),
                    "departmentId", user.departmentId() != null ? user.departmentId().toString() : ""
                )
        ));
    }

    /**
     * List all available mock users (for dev convenience).
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> listUsers() {
        return ResponseEntity.ok(Map.of("users", mockUsers.getAllUsersMap()));
    }
}
