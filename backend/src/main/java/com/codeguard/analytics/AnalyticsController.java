package com.codeguard.analytics;

import com.codeguard.auth.CurrentUser;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@Validated
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final CurrentUser users;
    public AnalyticsController(AnalyticsService analytics, CurrentUser users) { this.analytics = analytics; this.users = users; }
    @GetMapping
    public AnalyticsService.Dashboard get(Authentication auth, @RequestParam(defaultValue = "30") @Min(7) @Max(365) int days, @RequestParam(required = false) UUID repositoryId) {
        return analytics.get(users.require(auth).getId(), repositoryId, days);
    }
}
