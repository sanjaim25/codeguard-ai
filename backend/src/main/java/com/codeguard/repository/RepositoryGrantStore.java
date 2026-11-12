package com.codeguard.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface RepositoryGrantStore extends JpaRepository<RepositoryGrant, UUID> {
    @Query("select g from RepositoryGrant g join fetch g.repository r join fetch r.installation where g.user.id = :userId and r.id = :repositoryId and g.expiresAt > :now and r.active = true and r.installation.active = true")
    Optional<RepositoryGrant> validGrant(UUID userId, UUID repositoryId, Instant now);
    Optional<RepositoryGrant> findByUserIdAndRepositoryId(UUID userId, UUID repositoryId);
    void deleteByUserId(UUID userId);
    void deleteByRepositoryId(UUID repositoryId);
}
