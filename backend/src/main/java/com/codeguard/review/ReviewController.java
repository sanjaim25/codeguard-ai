package com.codeguard.review;
import com.codeguard.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReviewController {
    private final CurrentUser users;
    private final ReviewQueryService reviews;
    public ReviewController(CurrentUser users, ReviewQueryService reviews) { this.users = users; this.reviews = reviews; }
    @GetMapping("/api/reviews/{id}")
    public ReviewQueryService.Detail get(Authentication auth, @PathVariable UUID id) { return reviews.get(users.require(auth).getId(), id); }
}
