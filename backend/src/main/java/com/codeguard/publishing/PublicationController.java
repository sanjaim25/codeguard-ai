package com.codeguard.publishing;

import com.codeguard.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews/{id}/publication")
public class PublicationController {
    private final CurrentUser users;
    private final PublicationService service;
    private final GithubReviewPublisher publisher;
    public PublicationController(CurrentUser users, PublicationService service, GithubReviewPublisher publisher) {
        this.users = users; this.service = service; this.publisher = publisher;
    }
    @GetMapping public PublicationService.View get(Authentication auth, @PathVariable UUID id) { return service.view(users.require(auth).getId(), id); }
    @PostMapping public PublicationService.View request(Authentication auth, @PathVariable UUID id) { return service.request(users.require(auth).getId(), id); }
    @PostMapping("/reconcile") public PublicationService.View reconcile(Authentication auth, @PathVariable UUID id) {
        var userId = users.require(auth).getId();
        var claim = service.reconciliation(userId, id);
        var found = publisher.reconcile(claim.id(), claim.installationId(), claim.repository(), claim.number(), claim.sha());
        service.outcome(claim.id(), found.isPresent() ? found.getAsLong() : null);
        return service.view(userId, id);
    }
}
