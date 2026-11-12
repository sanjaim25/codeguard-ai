package com.codeguard.repository;

import com.codeguard.auth.CurrentUser;
import com.codeguard.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
@Validated
public class RepositoryController {
    private final CurrentUser currentUser;
    private final RepositoryService service;
    public RepositoryController(CurrentUser currentUser, RepositoryService service) { this.currentUser = currentUser; this.service = service; }
    @GetMapping
    public PageResponse<RepositoryService.Summary> list(Authentication auth,
        @RequestParam(defaultValue = "") @Size(max = 100) String search,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(currentUser.require(auth).getId(), search, page, size);
    }
    @GetMapping("/{id}")
    public RepositoryService.Detail get(Authentication auth, @PathVariable UUID id) { return service.get(currentUser.require(auth).getId(), id); }
    @PatchMapping("/{id}/policy")
    public RepositoryService.Summary update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody RepositoryService.Policy policy) {
        return service.updatePolicy(currentUser.require(auth).getId(), id, policy);
    }
}
