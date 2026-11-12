package com.codeguard.pullrequest;

import com.codeguard.common.*;
import com.codeguard.review.*;
import com.codeguard.security.RepositoryAccess;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PullRequestService {
    private final PullRequestStore pullRequests;
    private final ReviewJobStore jobs;
    private final RepositoryAccess access;
    private final AuditEventStore audit;
    public PullRequestService(PullRequestStore pullRequests, ReviewJobStore jobs, RepositoryAccess access, AuditEventStore audit) {
        this.pullRequests = pullRequests; this.jobs = jobs; this.access = access; this.audit = audit;
    }
    public record Summary(UUID id, UUID repositoryId, String repositoryName, int number, String title, String author,
                          String headSha, String headRef, String baseRef, String state, Instant updatedAt) {
        static Summary from(PullRequest pr) {
            return new Summary(pr.getId(), pr.getRepository().getId(), pr.getRepository().getFullName(), pr.getNumber(),
                pr.getTitle(), pr.getAuthor(), pr.getHeadSha(), pr.getHeadRef(), pr.getBaseRef(), pr.getState(), pr.getUpdatedAt());
        }
    }
    public record Job(UUID id, String commitSha, ReviewStatus status, int attempts, Instant createdAt,
                      Instant startedAt, Instant completedAt, String failureCode) {
        public static Job from(ReviewJob job) {
            return new Job(job.getId(), job.getCommitSha(), job.getStatus(), job.getAttempts(), job.getCreatedAt(),
                job.getStartedAt(), job.getCompletedAt(), job.getFailureCode());
        }
    }
    public record Detail(Summary pullRequest, List<Job> reviews) {}
    public PageResponse<Summary> list(UUID userId, UUID repositoryId, String search, String state, int page, int size) {
        return PageResponse.from(pullRequests.visibleTo(userId, Instant.now(), repositoryId, search, state,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))).map(Summary::from));
    }
    public Detail get(UUID userId, UUID id) {
        var pr = pullRequests.findById(id).orElseThrow(ResourceNotFoundException::new);
        access.read(userId, pr.getRepository().getId());
        return new Detail(Summary.from(pr), jobs.findByPullRequestIdOrderByCreatedAtDesc(id).stream().map(Job::from).toList());
    }
    @Transactional
    public Job cancel(UUID userId, UUID id) {
        var job = jobs.lockById(id).orElseThrow(ResourceNotFoundException::new);
        access.write(userId, job.getPullRequest().getRepository().getId());
        if (job.getStatus() == ReviewStatus.CANCELLED) return Job.from(job);
        if (!job.cancel(Instant.now())) throw new IllegalStateException("Only unfinished reviews can be cancelled");
        audit.save(new AuditEvent(userId, "REVIEW_CANCELLED", id, "Cancelled by an authorized repository writer"));
        return Job.from(job);
    }
}
