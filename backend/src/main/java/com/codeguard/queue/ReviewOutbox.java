package com.codeguard.queue;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_outbox", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "kind"}))
public class ReviewOutbox extends BaseEntity {
    public enum Kind { REVIEW, DEAD_LETTER }
    @Column(nullable = false) private UUID jobId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Kind kind;
    @Column(nullable = false) private Instant availableAt;
    private Instant sentAt;
    @Column(nullable = false) private int publishAttempts;
    protected ReviewOutbox() {}
    public ReviewOutbox(UUID jobId, Kind kind, Instant availableAt) { this.jobId = jobId; this.kind = kind; this.availableAt = availableAt; }
    public void rearm(Instant availableAt) { this.availableAt = availableAt; this.sentAt = null; }
    public void confirmed(Instant now) { this.sentAt = now; publishAttempts++; }
    public void deferred(Instant next) { availableAt = next; publishAttempts++; }
    public UUID getJobId() { return jobId; }
    public Kind getKind() { return kind; }
    public Instant getSentAt() { return sentAt; }
    public Instant getAvailableAt() { return availableAt; }
    public int getPublishAttempts() { return publishAttempts; }
}
