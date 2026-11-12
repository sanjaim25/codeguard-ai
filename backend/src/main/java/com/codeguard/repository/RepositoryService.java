package com.codeguard.repository;

import com.codeguard.common.*;
import com.codeguard.security.RepositoryAccess;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RepositoryService {
    private final CodeRepositoryStore repositories;
    private final RepositoryAccess access;
    private final AuditEventStore audit;
    public RepositoryService(CodeRepositoryStore repositories, RepositoryAccess access, AuditEventStore audit) {
        this.repositories = repositories; this.access = access; this.audit = audit;
    }
    public record Summary(UUID id, String fullName, boolean privateRepository, String language, boolean reviewEnabled,
                          boolean cloudAiOptIn, boolean publishingEnabled, boolean autoPublish) {
        static Summary from(CodeRepository repo) {
            return new Summary(repo.getId(), repo.getFullName(), repo.isPrivateRepository(), repo.getLanguage(), repo.isReviewEnabled(),
                repo.isCloudAiOptIn(), repo.isPublishingEnabled(), repo.isAutoPublish());
        }
    }
    public record Detail(Summary repository, boolean canManage, String installationAccount) {}
    public record Policy(boolean reviewEnabled, boolean cloudAiOptIn, boolean publishingEnabled, boolean autoPublish) {
        @AssertTrue public boolean isPublishingPolicyValid() { return !autoPublish || publishingEnabled; }
    }
    public PageResponse<Summary> list(UUID userId, String search, int page, int size) {
        return PageResponse.from(repositories.visibleTo(userId, Instant.now(), search, PageRequest.of(page, size, Sort.by("fullName"))).map(Summary::from));
    }
    public Detail get(UUID userId, UUID repositoryId) {
        var grant = access.read(userId, repositoryId);
        return new Detail(Summary.from(grant.getRepository()), grant.canWrite(), grant.getRepository().getInstallation().getAccountLogin());
    }
    @Transactional
    public Summary updatePolicy(UUID userId, UUID repositoryId, Policy policy) {
        var repo = access.write(userId, repositoryId);
        if (repo.getGithubRepositoryId() < 0 && (policy.cloudAiOptIn() || policy.publishingEnabled() || policy.autoPublish()))
            throw new IllegalArgumentException("Demo repositories cannot enable external AI or publishing");
        repo.updatePolicy(policy.reviewEnabled(), policy.cloudAiOptIn(), policy.publishingEnabled(), policy.autoPublish());
        audit.save(new AuditEvent(userId, "REPOSITORY_POLICY_UPDATED", repositoryId, policy.toString()));
        return Summary.from(repo);
    }
}
