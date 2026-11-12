package com.codeguard.publishing;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

/** One submission attempt per job. Ambiguous outcomes can only be reconciled, never resubmitted. */
@Entity
@Table(name = "review_publications")
public class Publication extends BaseEntity {
    public enum Status { PENDING, SENDING, PUBLISHED, UNKNOWN, FAILED, CANCELLED }
    @Column(nullable = false, unique = true) private UUID jobId;
    private UUID requestedBy;
    @Column(nullable = false) private boolean automatic;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
    private Instant sendingAt;
    private Long githubReviewId;
    @Column(length = 60) private String failureCode;
    @Version private long version;
    protected Publication() {}
    public Publication(UUID jobId, UUID requestedBy, boolean automatic) {
        this.jobId = jobId; this.requestedBy = requestedBy; this.automatic = automatic;
    }
    public boolean begin(Instant now) {
        if (status != Status.PENDING) return false;
        status = Status.SENDING; sendingAt = now; return true;
    }
    public void published(long reviewId) {
        if (reviewId <= 0) throw new IllegalArgumentException("Invalid GitHub review ID");
        if (status != Status.SENDING && status != Status.UNKNOWN) return;
        githubReviewId = reviewId; status = Status.PUBLISHED; failureCode = null;
    }
    public void unknown() {
        if (status == Status.SENDING) { status = Status.UNKNOWN; failureCode = "GITHUB_OUTCOME_UNKNOWN"; }
    }
    public void fail(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,60}")) throw new IllegalArgumentException("Invalid failure code");
        if (status == Status.PENDING) { status = Status.FAILED; failureCode = code; }
    }
    /** Only call when no submission POST has been attempted. */
    public void rejectBeforeSend(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,60}")) throw new IllegalArgumentException("Invalid failure code");
        if (status == Status.SENDING) { status = Status.FAILED; failureCode = code; }
    }
    public void cancel() { if (status == Status.PENDING) status = Status.CANCELLED; }
    public boolean abandoned(Instant now) {
        return status == Status.SENDING && sendingAt != null && sendingAt.plus(Duration.ofMinutes(5)).isBefore(now);
    }
    public UUID getJobId() { return jobId; }
    public UUID getRequestedBy() { return requestedBy; }
    public boolean isAutomatic() { return automatic; }
    public Status getStatus() { return status; }
    public Long getGithubReviewId() { return githubReviewId; }
    public String getFailureCode() { return failureCode; }
}
