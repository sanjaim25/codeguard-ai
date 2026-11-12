package com.codeguard.review;

import com.codeguard.ai.*;
import com.codeguard.analysis.*;
import com.codeguard.queue.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short database transactions; GitHub and AI network calls run in ReviewWorker outside these locks. */
@Service
@Transactional
public class ReviewExecution {
    public record Claim(UUID jobId, UUID token, int attempt, long installationId, String repository, int number, String sha) {}
    public record Prepared(AIReviewProvider.Input input, String skipReason) {}
    private final ReviewJobStore jobs;
    private final ReviewResultStore results;
    private final ReviewFindingStore findings;
    private final AiUsageStore usage;
    private final AiBudgetStore budgets;
    private final ReviewOutboxStore outbox;
    private final com.codeguard.publishing.PublicationStore publications;
    private final AIReviewProvider provider;
    private final AiSettings settings;
    private final ObjectMapper json;
    private final Clock clock;
    private final int maxAttempts;
    public ReviewExecution(ReviewJobStore jobs, ReviewResultStore results, ReviewFindingStore findings, AiUsageStore usage,
        AiBudgetStore budgets, ReviewOutboxStore outbox, com.codeguard.publishing.PublicationStore publications, AIReviewProvider provider, AiSettings settings, ObjectMapper json, Clock clock,
        @Value("${codeguard.queue.max-attempts:3}") int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("Review attempts must be 1–10");
        this.jobs = jobs; this.results = results; this.findings = findings; this.usage = usage; this.budgets = budgets;
        this.outbox = outbox; this.publications = publications; this.provider = provider; this.settings = settings; this.json = json; this.clock = clock; this.maxAttempts = maxAttempts;
    }
    public Claim claim(UUID id) {
        var job = jobs.lockById(id).orElse(null);
        if (job == null) return null;
        if (!eligible(job)) { job.cancel(clock.instant()); return null; }
        UUID token = job.claim(clock.instant(), Duration.ofMinutes(10));
        if (token == null) return null;
        var pr = job.getPullRequest(); var repo = pr.getRepository();
        return new Claim(id, token, job.getAttempts(), repo.getInstallation().getGithubInstallationId(), repo.getFullName(), pr.getNumber(), job.getCommitSha());
    }
    public Prepared prepare(Claim claim, List<ChangedFile> files) {
        var job = owned(claim);
        if (job == null) return null;
        var repo = job.getPullRequest().getRepository();
        if (!eligible(job)) { job.cancel(clock.instant()); return null; }
        var input = new AIReviewProvider.Input(files, repo.isPrivateRepository(), repo.isCloudAiOptIn());
        if (files.isEmpty()) return new Prepared(input, "No eligible text changes were available for analysis.");
        if (provider.external() && repo.isPrivateRepository() && !repo.isCloudAiOptIn())
            return new Prepared(input, "Cloud AI skipped: private repository consent has not been granted. Only local static checks ran.");
        if (budgets.existsByJobIdAndAttempt(job.getId(), job.getAttempts())) throw new AiProviderException("AI_ATTEMPT_ALREADY_RESERVED", false);
        var reservation = provider.reservation(input);
        if (budgets.totalReserved(job.getId()).add(reservation).compareTo(settings.maxCostUsd()) > 0) throw new AiProviderException("AI_REVIEW_COST_BUDGET", false);
        budgets.save(new AiBudgetReservation(job.getId(), job.getAttempts(), reservation));
        return new Prepared(input, null);
    }
    public boolean complete(Claim claim, DiffNormalizer.Normalized diff, String providerName, String summary,
        List<FindingData> data) {
        var job = owned(claim);
        if (job == null) return false;
        if (!eligible(job)) { job.cancel(clock.instant()); return false; }
        String serialized;
        try { serialized = json.writeValueAsString(diff.files()); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Could not store normalized diff"); }
        var result = results.save(new ReviewResult(job, providerName, summary, serialized, diff.excludedFiles(), diff.incomplete()));
        findings.saveAll(data.stream().map(finding -> new ReviewFinding(result, finding)).toList());
        job.complete(claim.token(), clock.instant());
        var repo = job.getPullRequest().getRepository();
        if (repo.isPublishingEnabled() && repo.isAutoPublish() && repo.getGithubRepositoryId() > 0)
            publications.save(new com.codeguard.publishing.Publication(job.getId(), null, true));
        return true;
    }
    public void recordUsage(Claim claim, String providerName, AIReviewProvider.Usage aiUsage) {
        // A cancelled or superseded attempt may still have incurred provider usage.
        var job = jobs.lockById(claim.jobId()).orElse(null);
        if (job != null && budgets.existsByJobIdAndAttempt(job.getId(), claim.attempt()) && !usage.existsByJobIdAndAttempt(job.getId(), claim.attempt()))
            usage.save(new AiUsage(job.getId(), claim.attempt(), providerName, aiUsage.model(), aiUsage.inputTokens(), aiUsage.outputTokens(), aiUsage.measuredTokens(), aiUsage.estimatedCostUsd()));
    }
    public void fail(Claim claim, String code, boolean retryable, long retryAfterSeconds) {
        var job = owned(claim);
        if (job == null) return;
        job.fail(claim.token(), clock.instant(), code, retryable, maxAttempts);
        job.deferUntil(clock.instant().plusSeconds(Math.max(0, Math.min(3600, retryAfterSeconds))));
        schedule(job);
    }
    public void cancel(Claim claim) { var job = owned(claim); if (job != null) job.cancel(clock.instant()); }
    public void recover(UUID id) {
        var job = jobs.lockById(id).orElse(null);
        if (job != null && (job.recoverExpired(clock.instant(), maxAttempts) || job.recoverQueued(clock.instant()))) schedule(job);
    }
    private void schedule(ReviewJob job) {
        var kind = job.getStatus() == ReviewStatus.FAILED ? ReviewOutbox.Kind.DEAD_LETTER : ReviewOutbox.Kind.REVIEW;
        Instant available = kind == ReviewOutbox.Kind.REVIEW ? job.getAvailableAt() : clock.instant();
        var message = outbox.findByJobIdAndKind(job.getId(), kind).orElseGet(() -> new ReviewOutbox(job.getId(), kind, available));
        message.rearm(available); outbox.save(message);
    }
    private ReviewJob owned(Claim claim) {
        return jobs.lockById(claim.jobId()).filter(job -> job.ownsClaim(claim.token(), clock.instant())).orElse(null);
    }
    private boolean eligible(ReviewJob job) {
        var pr = job.getPullRequest(); var repo = pr.getRepository();
        return repo.isActive() && repo.getInstallation().isActive() && repo.isReviewEnabled() && pr.getState().equals("open") && pr.getHeadSha().equals(job.getCommitSha());
    }
}
