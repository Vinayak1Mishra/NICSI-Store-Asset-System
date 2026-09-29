package com.nicsi.store.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filter to extract and validate JWT tokens from the Authorization header.
 * Sets the SecurityContext with the CurrentUser if the token is valid.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final String jwtSecret;

    public JwtAuthenticationFilter(@Value("${nicsi.security.jwt-secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
                
                UUID userId = UUID.fromString(claims.getSubject());
                String username = claims.get("username", String.class);
                String name = claims.get("name", String.class);
                List<String> rolesList = claims.get("roles", List.class);
                List<String> permissionsList = claims.get("permissions", List.class);
                String deptIdStr = claims.get("department_id", String.class);
                UUID departmentId = deptIdStr != null ? UUID.fromString(deptIdStr) : null;
                
                Set<String> roles = rolesList != null ? Set.copyOf(rolesList) : Set.of();
                Set<String> permissions = permissionsList != null ? Set.copyOf(permissionsList) : Set.of();
                
                CurrentUser user = new CurrentUser(
                    userId,
                    username,
                    name,
                    roles,
                    permissions,
                    departmentId,
                    request.getRemoteAddr()
                );
                
                List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
                    
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(user, null, authorities);
                    
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
            } catch (JwtException | IllegalArgumentException e) {
                // Invalid token; leave SecurityContext empty and allow Spring Security to handle 401
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
