package com.nicsi.store.common.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nicsi.store.common.error.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Utility class to access the current authenticated user from the SecurityContext.
 */
public final class CurrentUserHolder {
    private CurrentUserHolder() {}
    
    /**
     * Retrieves the current authenticated user.
     * @return CurrentUser information
     * @throws BusinessException if no user is authenticated
     */
    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException("AUTH_REQUIRED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
    
    /**
     * Convenience method to get the current user's ID.
     * @return UUID of the user
     */
    public static UUID getUserId() {
        return get().userId();
    }
}
