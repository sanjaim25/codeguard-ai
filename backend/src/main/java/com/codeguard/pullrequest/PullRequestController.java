package com.codeguard.pullrequest;

import com.codeguard.auth.CurrentUser;
import com.codeguard.common.PageResponse;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class PullRequestController {
    private final CurrentUser currentUser;
    private final PullRequestService service;
    public PullRequestController(CurrentUser currentUser, PullRequestService service) { this.currentUser = currentUser; this.service = service; }
    @GetMapping("/api/pull-requests")
    public PageResponse<PullRequestService.Summary> list(Authentication auth,
        @RequestParam(required = false) UUID repositoryId,
        @RequestParam(defaultValue = "") @Size(max = 100) String search,
        @RequestParam(defaultValue = "") @Pattern(regexp = "|open|closed") String state,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(currentUser.require(auth).getId(), repositoryId, search, state, page, size);
    }
    @GetMapping("/api/pull-requests/{id}")
    public PullRequestService.Detail get(Authentication auth, @PathVariable UUID id) { return service.get(currentUser.require(auth).getId(), id); }
    @PostMapping("/api/reviews/{id}/cancel")
    public PullRequestService.Job cancel(Authentication auth, @PathVariable UUID id) { return service.cancel(currentUser.require(auth).getId(), id); }
}
