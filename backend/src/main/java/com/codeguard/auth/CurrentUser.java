package com.codeguard.auth;

import com.codeguard.user.*;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CurrentUser {
    private final UserAccountRepository users;
    public CurrentUser(UserAccountRepository users) { this.users = users; }
    public UserAccount require(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Authentication required");
        if (auth.getPrincipal() instanceof OAuth2User oauth) {
            Object id = oauth.getAttribute("id");
            if (id instanceof Number number) return users.findByGithubUserId(number.longValue())
                .orElseThrow(() -> new AccessDeniedException("Account is not registered"));
            throw new AccessDeniedException("Invalid user identity");
        }
        // UUID principals are issued only by the explicit local demo authentication flow.
        try {
            return users.findById(UUID.fromString(auth.getName())).orElseThrow(() -> new AccessDeniedException("Account is not registered"));
        } catch (IllegalArgumentException invalid) { throw new AccessDeniedException("Invalid user identity"); }
    }
}
