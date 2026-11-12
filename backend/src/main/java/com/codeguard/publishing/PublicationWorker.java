package com.codeguard.publishing;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codeguard.queue.enabled", havingValue = "true", matchIfMissing = true)
public class PublicationWorker {
    private static final Logger log = LoggerFactory.getLogger(PublicationWorker.class);
    private final PublicationStore store;
    private final PublicationService service;
    private final GithubReviewPublisher publisher;
    public PublicationWorker(PublicationStore store, PublicationService service, GithubReviewPublisher publisher) {
        this.store = store; this.service = service; this.publisher = publisher;
    }
    @Scheduled(fixedDelayString = "${codeguard.publishing.poll-ms:10000}")
    public void dispatch() {
        for (UUID id : store.actionable(PageRequest.of(0, 10))) {
            try { process(id); }
            catch (RuntimeException failure) { log.warn("event=publication_failure id={} exception_type={}", id, failure.getClass().getSimpleName()); }
        }
    }
    public void process(UUID id) {
        var claim = service.claim(id);
        if (claim == null) return;
        PublicationPayload payload;
        try {
            payload = publisher.prepare(id, claim.installationId(), claim.repository(), claim.number(), claim.sha(), claim.summary(), claim.findings());
            if (!service.permitted(claim)) { service.rejectBeforeSend(id, "PUBLISH_POLICY_CHANGED"); return; }
        } catch (RuntimeException failure) {
            service.rejectBeforeSend(id, failure instanceof com.codeguard.github.StalePullRequestException ? "PUBLISH_STALE_COMMIT" : "PUBLISH_PREFLIGHT_FAILED");
            return;
        }
        try {
            long reviewId = publisher.submitOnce(claim.installationId(), claim.repository(), claim.number(), payload);
            service.outcome(id, reviewId);
        } catch (RuntimeException failure) {
            service.outcome(id, null);
            log.warn("event=publication_outcome_unknown id={} exception_type={}", id, failure.getClass().getSimpleName());
        }
    }
}
