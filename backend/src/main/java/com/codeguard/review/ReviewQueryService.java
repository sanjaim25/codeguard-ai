package com.codeguard.review;

import com.codeguard.analysis.*;
import com.codeguard.ai.*;
import com.codeguard.common.ResourceNotFoundException;
import com.codeguard.pullrequest.PullRequestService;
import com.codeguard.security.RepositoryAccess;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewQueryService {
    private final ReviewJobStore jobs;
    private final ReviewResultStore results;
    private final ReviewFindingStore findings;
    private final AiUsageStore usage;
    private final RepositoryAccess access;
    private final ObjectMapper json;
    public ReviewQueryService(ReviewJobStore jobs, ReviewResultStore results, ReviewFindingStore findings, AiUsageStore usage, RepositoryAccess access, ObjectMapper json) {
        this.jobs = jobs; this.results = results; this.findings = findings; this.usage = usage; this.access = access; this.json = json;
    }
    public record Finding(UUID id, FindingData data) {}
    public record Usage(String provider, String model, long inputTokens, long outputTokens, boolean measuredTokens, BigDecimal estimatedCostUsd) {}
    public record Detail(PullRequestService.Job job, String provider, String summary, List<ChangedFile> files, List<Finding> findings,
                         int excludedFiles, boolean incomplete, List<Usage> usage) {}
    public Detail get(UUID userId, UUID jobId) {
        var job = jobs.findById(jobId).orElseThrow(ResourceNotFoundException::new);
        access.read(userId, job.getPullRequest().getRepository().getId());
        var result = results.findByJobId(jobId).orElse(null);
        List<ChangedFile> files = List.of();
        if (result != null) {
            try { files = json.readValue(result.getDiffJson(), new TypeReference<>() {}); }
            catch (java.io.IOException corrupt) { throw new IllegalStateException("Stored diff could not be decoded"); }
        }
        return new Detail(PullRequestService.Job.from(job), result == null ? null : result.getProvider(),
            result == null ? null : result.getSummary(), files,
            result == null ? List.of() : findings.findByResultIdOrderByCreatedAtAsc(result.getId()).stream().map(f -> new Finding(f.getId(), f.data())).toList(),
            result == null ? 0 : result.getExcludedFiles(), result != null && result.isIncomplete(),
            usage.findByJobIdOrderByCreatedAtAsc(jobId).stream().map(u -> new Usage(u.getProvider(), u.getModel(), u.getInputTokens(), u.getOutputTokens(), u.isMeasuredTokens(), u.getEstimatedCostUsd())).toList());
    }
}
