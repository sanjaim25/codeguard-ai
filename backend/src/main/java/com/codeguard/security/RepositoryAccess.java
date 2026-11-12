package com.codeguard.security;

import com.codeguard.common.ResourceNotFoundException;
import com.codeguard.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RepositoryAccess {
    private final RepositoryGrantStore grants;
    public RepositoryAccess(RepositoryGrantStore grants) { this.grants = grants; }
    public RepositoryGrant read(UUID userId, UUID repositoryId) {
        return grants.validGrant(userId, repositoryId, Instant.now()).orElseThrow(ResourceNotFoundException::new);
    }
    public CodeRepository write(UUID userId, UUID repositoryId) {
        var grant = read(userId, repositoryId);
        if (!grant.canWrite()) throw new AccessDeniedException("Write access required");
        return grant.getRepository();
    }
}
