package com.codeguard.queue;

import com.codeguard.review.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "codeguard.queue.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDelivery {
    private final ReviewJobStore jobs;
    private final ReviewOutboxStore outbox;
    private final ReviewMessagePublisher publisher;
    private final Clock clock;
    public OutboxDelivery(ReviewJobStore jobs, ReviewOutboxStore outbox, ReviewMessagePublisher publisher, Clock clock) {
        this.jobs = jobs; this.outbox = outbox; this.publisher = publisher; this.clock = clock;
    }
    @Transactional
    public void deliver(UUID jobId, ReviewOutbox.Kind kind) {
        // Consistent job-before-outbox lock order matches worker retry/recovery transactions.
        var job = jobs.lockById(jobId).orElse(null);
        if (job == null) return;
        var message = outbox.findByJobIdAndKind(jobId, kind).orElse(null);
        if (message == null || message.getSentAt() != null || message.getAvailableAt().isAfter(clock.instant())) return;
        boolean applicable = kind == ReviewOutbox.Kind.REVIEW ? job.getStatus() == ReviewStatus.PENDING || job.getStatus() == ReviewStatus.QUEUED : job.getStatus() == ReviewStatus.FAILED;
        if (!applicable) { message.confirmed(clock.instant()); return; }
        // Keep the short row lock until confirm so a fast consumer cannot race the queued transition.
        if (publisher.publish(jobId, kind)) {
            message.confirmed(clock.instant());
            if (kind == ReviewOutbox.Kind.REVIEW) job.markQueued();
        } else message.deferred(clock.instant().plusSeconds(Math.min(300, 5L << Math.min(message.getPublishAttempts(), 6))));
    }
}
