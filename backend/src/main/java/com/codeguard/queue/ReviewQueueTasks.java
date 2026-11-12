package com.codeguard.queue;

import com.codeguard.review.*;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codeguard.queue.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewQueueTasks {
    private static final Logger log = LoggerFactory.getLogger(ReviewQueueTasks.class);
    private final ReviewOutboxStore outbox;
    private final ReviewJobStore jobs;
    private final OutboxDelivery delivery;
    private final ReviewExecution execution;
    private final Clock clock;
    public ReviewQueueTasks(ReviewOutboxStore outbox, ReviewJobStore jobs, OutboxDelivery delivery, ReviewExecution execution, Clock clock) {
        this.outbox = outbox; this.jobs = jobs; this.delivery = delivery; this.execution = execution; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${codeguard.queue.dispatch-delay-ms:1000}", initialDelay = 5000)
    public void dispatch() {
        try {
            for (var message : outbox.pending(clock.instant(), PageRequest.of(0, 20))) delivery.deliver(message.getJobId(), message.getKind());
        } catch (RuntimeException failure) { log.warn("outbox_dispatch_unavailable error_type={}", failure.getClass().getSimpleName()); }
    }
    @Scheduled(fixedDelayString = "${codeguard.queue.recovery-delay-ms:30000}", initialDelay = 10000)
    public void recover() {
        try {
            for (var id : jobs.recoverable(clock.instant(), clock.instant().minusSeconds(600), PageRequest.of(0, 100))) execution.recover(id);
        } catch (RuntimeException failure) { log.warn("review_recovery_unavailable error_type={}", failure.getClass().getSimpleName()); }
    }
}
