package com.codeguard.github;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface InstallationRepository extends JpaRepository<GithubInstallation, UUID> {
    Optional<GithubInstallation> findByGithubInstallationId(long githubInstallationId);
    @Query("select distinct i from RepositoryGrant g join g.repository r join r.installation i where g.user.id = :userId and g.expiresAt > :now and r.active = true and i.active = true order by i.accountLogin")
    List<GithubInstallation> visibleTo(UUID userId, Instant now);
}
