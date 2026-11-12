package com.codeguard.review;

import com.codeguard.common.BaseEntity;
import com.codeguard.pullrequest.PullRequest;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "review_jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"pull_request_id", "commit_sha"}))
public class ReviewJob extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private PullRequest pullRequest;
    @Column(nullable = false, length = 64, updatable = false) private String commitSha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReviewStatus status = ReviewStatus.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant availableAt = Instant.now();
    private Instant startedAt;
    private Instant completedAt;
    private Instant leaseUntil;
    private UUID claimToken;
    @Column(length = 60) private String failureCode;
    @Version private long version;
    protected ReviewJob() {}
    public ReviewJob(PullRequest pullRequest) { this.pullRequest = pullRequest; this.commitSha = pullRequest.getHeadSha(); }

    public boolean markQueued() {
        if (status != ReviewStatus.PENDING) return false;
        status = ReviewStatus.QUEUED; return true;
    }
    /** Must be called under a database row lock. The token fences out superseded workers. */
    public UUID claim(Instant now, Duration lease) {
        if ((status != ReviewStatus.PENDING && status != ReviewStatus.QUEUED) || availableAt.isAfter(now)) return null;
        if (lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("A positive lease is required");
        status = ReviewStatus.PROCESSING; attempts++; startedAt = now; completedAt = null;
        leaseUntil = now.plus(lease); claimToken = UUID.randomUUID(); return claimToken;
    }
    public void complete(UUID token, Instant now) {
        requireClaim(token, now); status = ReviewStatus.COMPLETED; completedAt = now; clearClaim(); failureCode = null;
    }
    public void fail(UUID token, Instant now, String safeCode, boolean retryable, int maxAttempts) {
        requireClaim(token, now);
        if (safeCode == null || !safeCode.matches("[A-Z0-9_]{1,60}")) throw new IllegalArgumentException("Use a safe error code");
        failureCode = safeCode;
        if (retryable && attempts < maxAttempts) {
            status = ReviewStatus.PENDING;
            availableAt = now.plusSeconds(Math.min(300, 5L << Math.min(attempts - 1, 6)));
        } else { status = ReviewStatus.FAILED; completedAt = now; }
        clearClaim();
    }
    public boolean recoverExpired(Instant now, int maxAttempts) {
        if (status != ReviewStatus.PROCESSING || leaseUntil == null || leaseUntil.isAfter(now)) return false;
        failureCode = "WORKER_LEASE_EXPIRED";
        if (attempts >= maxAttempts) { status = ReviewStatus.FAILED; completedAt = now; }
        else { status = ReviewStatus.PENDING; availableAt = now; }
        clearClaim(); return true;
    }
    public boolean recoverQueued(Instant now) {
        if (status != ReviewStatus.QUEUED || updatedAt.isAfter(now.minusSeconds(600))) return false;
        status = ReviewStatus.PENDING; availableAt = now; return true;
    }
    public boolean cancel(Instant now) {
        if (status == ReviewStatus.COMPLETED || status == ReviewStatus.FAILED || status == ReviewStatus.CANCELLED) return false;
        status = ReviewStatus.CANCELLED; completedAt = now; clearClaim(); return true;
    }
    public boolean ownsClaim(UUID token, Instant now) {
        return status == ReviewStatus.PROCESSING && claimToken != null && claimToken.equals(token) && leaseUntil.isAfter(now);
    }
    public void deferUntil(Instant next) {
        if (status == ReviewStatus.PENDING && next.isAfter(availableAt)) availableAt = next;
    }
    private void requireClaim(UUID token, Instant now) {
        if (!ownsClaim(token, now))
            throw new IllegalStateException("Worker no longer owns this job");
    }
    private void clearClaim() { claimToken = null; leaseUntil = null; }
    public PullRequest getPullRequest() { return pullRequest; }
    public String getCommitSha() { return commitSha; }
    public ReviewStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getFailureCode() { return failureCode; }
}
