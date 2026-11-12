package com.codeguard.pullrequest;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PullRequestStore extends JpaRepository<PullRequest, UUID> {
    Optional<PullRequest> findByRepositoryIdAndNumber(UUID repositoryId, int number);
    @EntityGraph(attributePaths = "repository")
    @Query("select p from PullRequest p join RepositoryGrant g on g.repository = p.repository where g.user.id = :userId and g.expiresAt > :now and p.repository.active = true and p.repository.installation.active = true and (:repositoryId is null or p.repository.id = :repositoryId) and (:state = '' or p.state = :state) and lower(p.title) like lower(concat('%', :search, '%'))")
    Page<PullRequest> visibleTo(UUID userId, Instant now, UUID repositoryId, String search, String state, Pageable pageable);
}
