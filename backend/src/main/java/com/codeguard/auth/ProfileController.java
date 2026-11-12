package com.codeguard.auth;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProfileController {
    private final CurrentUser currentUser;
    public ProfileController(CurrentUser currentUser) { this.currentUser = currentUser; }
    public record Profile(UUID id, String login, String avatarUrl) {}
    @GetMapping("/api/me")
    public Profile profile(Authentication auth) {
        var user = currentUser.require(auth);
        return new Profile(user.getId(), user.getLogin(), user.getAvatarUrl());
    }
}
