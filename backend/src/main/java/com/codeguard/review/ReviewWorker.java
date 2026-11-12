package com.codeguard.review;

import com.codeguard.ai.*;
import com.codeguard.analysis.*;
import com.codeguard.github.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReviewWorker {
    private static final Logger log = LoggerFactory.getLogger(ReviewWorker.class);
    private final ReviewExecution execution;
    private final GithubPullRequests github;
    private final DiffNormalizer diffs;
    private final StaticChecks staticChecks;
    private final FindingNormalizer findings;
    private final AIReviewProvider provider;
    public ReviewWorker(ReviewExecution execution, GithubPullRequests github, DiffNormalizer diffs, StaticChecks staticChecks, FindingNormalizer findings, AIReviewProvider provider) {
        this.execution = execution; this.github = github; this.diffs = diffs; this.staticChecks = staticChecks; this.findings = findings; this.provider = provider;
    }
    public void process(UUID jobId) {
        var claim = execution.claim(jobId);
        if (claim == null) return;
        log.info("event=review_claimed job_id={} attempt={}", jobId, claim.attempt());
        try {
            var diff = diffs.normalize(github.snapshot(claim.installationId(), claim.repository(), claim.number(), claim.sha()));
            List<FindingData> collected = new ArrayList<>(staticChecks.analyze(diff.files()));
            var prepared = execution.prepare(claim, diff.files());
            if (prepared == null) return;
            String providerName = "static", summary = prepared.skipReason();
            if (prepared.skipReason() == null) {
                var output = provider.review(prepared.input());
                summary = output.summary(); collected.addAll(output.findings()); providerName = provider.name();
                execution.recordUsage(claim, providerName, output.usage());
            }
            boolean completed = execution.complete(claim, diff, providerName, summary, findings.normalize(collected, diff.files()));
            log.info("event=review_completed job_id={} completed={} provider={} finding_count={}", jobId, completed, providerName, collected.size());
        } catch (StalePullRequestException stale) {
            log.info("event=review_cancelled job_id={} reason=stale_pull_request", jobId);
            execution.cancel(claim);
        }
        catch (GithubApiException error) {
            log.warn("event=review_deferred job_id={} source=github retryable={} retry_after_seconds={}", jobId, error.retryable(), error.retryAfterSeconds());
            execution.fail(claim, "GITHUB_API_ERROR", error.retryable(), error.retryAfterSeconds());
        }
        catch (AiProviderException error) {
            log.warn("event=review_deferred job_id={} source=ai code={} retryable={} retry_after_seconds={}", jobId, error.code(), error.retryable(), error.retryAfterSeconds());
            execution.fail(claim, error.code(), error.retryable(), error.retryAfterSeconds());
        }
        catch (RuntimeException error) {
            // Neither exception messages nor bodies are logged: they may contain private code.
            log.warn("review_failed job={} error_type={}", jobId, error.getClass().getSimpleName());
            execution.fail(claim, "REVIEW_INTERNAL_ERROR", true, 0);
        }
    }
}
