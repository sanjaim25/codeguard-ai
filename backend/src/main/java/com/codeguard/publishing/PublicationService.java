package com.codeguard.publishing;

import com.codeguard.analysis.*;
import com.codeguard.common.*;
import com.codeguard.review.*;
import com.codeguard.security.RepositoryAccess;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicationService {
    public record View(UUID id, String status, String failureCode, String githubUrl, boolean canPublish, PublicationPayload preview) {}
    public record Claim(UUID id, UUID jobId, long installationId, String repository, int number, String sha,
                        String summary, List<FindingData> findings) {}
    private final PublicationStore publications;
    private final ReviewJobStore jobs;
    private final ReviewResultStore results;
    private final ReviewFindingStore findings;
    private final RepositoryAccess access;
    private final AuditEventStore audit;
    private final ObjectMapper json;
    private final Clock clock;
    public PublicationService(PublicationStore publications, ReviewJobStore jobs, ReviewResultStore results,
                              ReviewFindingStore findings, RepositoryAccess access, AuditEventStore audit, ObjectMapper json, Clock clock) {
        this.publications = publications; this.jobs = jobs; this.results = results; this.findings = findings;
        this.access = access; this.audit = audit; this.json = json; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public View view(UUID userId, UUID jobId) {
        var job = job(jobId);
        var grant = access.read(userId, job.getPullRequest().getRepository().getId());
        var publication = publications.findByJobId(jobId).orElse(null);
        var result = results.findByJobId(jobId).orElse(null);
        PublicationPayload preview = null;
        if (result != null) {
            try {
                List<ChangedFile> files = json.readValue(result.getDiffJson(), new TypeReference<>() {});
                preview = PublicationPayload.create(publication == null ? jobId : publication.getId(), job.getCommitSha(), result.getSummary(), data(result), files);
            } catch (java.io.IOException corrupt) { throw new IllegalStateException("Stored diff could not be decoded"); }
        }
        var pr = job.getPullRequest();
        String url = publication != null && publication.getGithubReviewId() != null
            ? "https://github.com/" + pr.getRepository().getFullName() + "/pull/" + pr.getNumber() + "#pullrequestreview-" + publication.getGithubReviewId() : null;
        return new View(publication == null ? null : publication.getId(), publication == null ? "NOT_REQUESTED" : publication.getStatus().name(),
            publication == null ? null : publication.getFailureCode(), url, grant.canWrite() && eligible(job) && publication == null, preview);
    }
    public View request(UUID userId, UUID jobId) {
        var job = jobs.lockById(jobId).orElseThrow(ResourceNotFoundException::new);
        access.write(userId, job.getPullRequest().getRepository().getId());
        if (publications.findByJobId(jobId).isEmpty()) {
            if (!eligible(job)) throw new IllegalStateException("Publishing is unavailable");
            publications.save(new Publication(jobId, userId, false));
            audit.save(new AuditEvent(userId, "REVIEW_PUBLISH_REQUESTED", jobId, "Manual review publishing requested"));
        }
        return view(userId, jobId);
    }
    public Claim claim(UUID id) {
        var publication = publications.lockById(id).orElse(null);
        if (publication == null) return null;
        if (publication.abandoned(clock.instant())) publication.unknown();
        if (publication.getStatus() != Publication.Status.PENDING) return null;
        var job = job(publication.getJobId());
        if (!allowed(publication, job)) { publication.cancel(); return null; }
        var result = results.findByJobId(job.getId()).orElseThrow();
        publication.begin(clock.instant());
        var pr = job.getPullRequest(); var repo = pr.getRepository();
        return new Claim(id, job.getId(), repo.getInstallation().getGithubInstallationId(), repo.getFullName(), pr.getNumber(), job.getCommitSha(), result.getSummary(), data(result));
    }
    /** Recheck consent and actor authorization after network preparation, before the single POST. */
    public boolean permitted(Claim claim) {
        var publication = publications.lockById(claim.id()).orElseThrow();
        var job = job(claim.jobId()); var repo = job.getPullRequest().getRepository();
        return publication.getStatus() == Publication.Status.SENDING && allowed(publication, job)
            && claim.installationId() == repo.getInstallation().getGithubInstallationId() && claim.repository().equals(repo.getFullName());
    }
    public void outcome(UUID id, Long reviewId) {
        var publication = publications.lockById(id).orElseThrow();
        if (reviewId == null) publication.unknown(); else publication.published(reviewId);
        audit.save(new AuditEvent(publication.getRequestedBy(), reviewId == null ? "REVIEW_PUBLISH_UNKNOWN" : "REVIEW_PUBLISHED",
            publication.getJobId(), reviewId == null ? "Outcome requires read-only reconciliation" : "GitHub review ID " + reviewId));
    }
    public void rejectBeforeSend(UUID id, String code) {
        var publication = publications.lockById(id).orElseThrow();
        publication.rejectBeforeSend(code);
        audit.save(new AuditEvent(publication.getRequestedBy(), "REVIEW_PUBLISH_REJECTED", publication.getJobId(), code));
    }
    @Transactional(readOnly = true)
    public Claim reconciliation(UUID userId, UUID jobId) {
        var job = job(jobId); access.write(userId, job.getPullRequest().getRepository().getId());
        var publication = publications.findByJobId(jobId).orElseThrow(ResourceNotFoundException::new);
        if (publication.getStatus() != Publication.Status.UNKNOWN && !publication.abandoned(clock.instant()))
            throw new IllegalStateException("Publication is not awaiting reconciliation");
        var pr = job.getPullRequest(); var repo = pr.getRepository();
        return new Claim(publication.getId(), jobId, repo.getInstallation().getGithubInstallationId(), repo.getFullName(), pr.getNumber(), job.getCommitSha(), "", List.of());
    }
    private boolean allowed(Publication publication, ReviewJob job) {
        if (!eligible(job)) return false;
        if (publication.isAutomatic()) return job.getPullRequest().getRepository().isAutoPublish();
        try { access.write(publication.getRequestedBy(), job.getPullRequest().getRepository().getId()); return true; }
        catch (ResourceNotFoundException | org.springframework.security.access.AccessDeniedException revoked) { return false; }
    }
    private static boolean eligible(ReviewJob job) {
        var pr = job.getPullRequest(); var repo = pr.getRepository();
        return job.getStatus() == ReviewStatus.COMPLETED && repo.isActive() && repo.getInstallation().isActive()
            && repo.getGithubRepositoryId() > 0 && repo.getInstallation().getGithubInstallationId() > 0
            && repo.isPublishingEnabled() && pr.getState().equals("open") && pr.getHeadSha().equals(job.getCommitSha());
    }
    private ReviewJob job(UUID id) { return jobs.findById(id).orElseThrow(ResourceNotFoundException::new); }
    private List<FindingData> data(ReviewResult result) { return findings.findByResultIdOrderByCreatedAtAsc(result.getId()).stream().map(ReviewFinding::data).toList(); }
}
