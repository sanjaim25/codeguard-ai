package com.codeguard.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CodeRepositoryStore extends JpaRepository<CodeRepository, UUID> {
    Optional<CodeRepository> findByGithubRepositoryId(long githubRepositoryId);
    @Query("select r from CodeRepository r join RepositoryGrant g on g.repository = r where g.user.id = :userId and g.expiresAt > :now and r.active = true and r.installation.active = true and lower(r.fullName) like lower(concat('%', :search, '%'))")
    Page<CodeRepository> visibleTo(UUID userId, Instant now, String search, Pageable pageable);
}
