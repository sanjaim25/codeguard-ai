package com.codeguard.github;

import com.codeguard.repository.*;
import com.codeguard.pullrequest.*;
import com.codeguard.review.*;
import com.codeguard.queue.*;
import com.codeguard.user.UserAccountRepository;
import com.fasterxml.jackson.databind.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    public record Receipt(String outcome, UUID jobId, boolean duplicate) {}
    private final WebhookDeliveryStore deliveries;
    private final InstallationRepository installations;
    private final CodeRepositoryStore repositories;
    private final RepositoryGrantStore grants;
    private final UserAccountRepository users;
    private final PullRequestStore pullRequests;
    private final ReviewJobStore jobs;
    private final ReviewOutboxStore outbox;
    private final InstallationTokens tokens;
    private final GithubClient github;
    private final AppJwtSigner signer;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final GithubAccessFence accessFence;
    private final long appId;
    public WebhookService(WebhookDeliveryStore deliveries, InstallationRepository installations, CodeRepositoryStore repositories,
                          RepositoryGrantStore grants, UserAccountRepository users, PullRequestStore pullRequests, ReviewJobStore jobs,
                          ReviewOutboxStore outbox, InstallationTokens tokens, GithubClient github, AppJwtSigner signer, JdbcTemplate jdbc,
                          ObjectMapper json, GithubAccessFence accessFence, @Value("${codeguard.github.app-id:0}") String appId) {
        this.deliveries = deliveries; this.installations = installations; this.repositories = repositories; this.grants = grants;
        this.users = users; this.pullRequests = pullRequests; this.jobs = jobs; this.outbox = outbox; this.tokens = tokens;
        this.github = github; this.signer = signer; this.jdbc = jdbc; this.json = json; this.appId = appId.isBlank() ? 0 : Long.parseLong(appId);
        this.accessFence = accessFence;
    }
    /** Called only after raw-body authentication. Event, PR, job and outbox persist atomically. */
    @Transactional
    public Receipt processVerified(String deliveryId, String event, byte[] body) {
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
        lock("delivery:" + deliveryId);
        var prior = deliveries.findByDeliveryId(deliveryId);
        if (prior.isPresent()) {
            if (!prior.get().getBodySha256().equals(hash)) throw new WebhookRejectedException(HttpStatus.CONFLICT, "Delivery ID was already used with different content");
            log.info("event=webhook_duplicate event_type={} outcome={} job_id={}", event, prior.get().getOutcome(), prior.get().getJobId());
            return new Receipt(prior.get().getOutcome(), prior.get().getJobId(), true);
        }
        JsonNode payload;
        try { payload = json.readTree(body); }
        catch (java.io.IOException invalid) { throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Invalid webhook JSON"); }
        if (payload == null || !payload.isObject()) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Invalid webhook JSON");
        if (Set.of("installation", "installation_repositories", "github_app_authorization").contains(event)) {
            accessFence.lock();
            accessFence.changed();
        }
        Receipt receipt = process(event, payload);
        deliveries.save(new WebhookDelivery(deliveryId, event, hash, receipt.outcome(), receipt.jobId()));
        log.info("event=webhook_processed event_type={} outcome={} job_id={}", event, receipt.outcome(), receipt.jobId());
        return receipt;
    }
    private Receipt process(String event, JsonNode payload) {
        if (event.equals("ping")) return receipt("PING");
        if (event.equals("github_app_authorization") && payload.path("action").asText().equals("revoked")) {
            users.findByGithubUserId(payload.path("sender").path("id").asLong()).ifPresent(user -> grants.deleteByUserId(user.getId()));
            return receipt("ACCESS_REVOKED");
        }
        if (!Set.of("installation", "installation_repositories", "pull_request").contains(event)) return receipt("IGNORED_EVENT");
        long installationId = payload.path("installation").path("id").asLong();
        if (installationId < 1) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Installation ID is required");
        lock("installation:" + installationId);
        String action = payload.path("action").asText();
        if (event.equals("installation")) {
            var remote = payload.path("installation");
            if (remote.path("app_id").asLong() != appId || appId < 1) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Installation does not belong to the configured App");
            var installation = installations.findByGithubInstallationId(installationId).orElseGet(() -> new GithubInstallation(installationId, text(remote.path("account"), "login", 100)));
            if (Set.of("deleted", "suspend").contains(action)) installation.setActive(false);
            else if (Set.of("created", "unsuspend").contains(action)) installation.setActive(true);
            installations.save(installation); tokens.invalidate(installationId);
            return receipt("INSTALLATION_UPDATED");
        }
        var installation = installations.findByGithubInstallationId(installationId).orElse(null);
        if (installation == null) {
            // Handles a PR arriving before the installation webhook, without trusting a claimed installation ID.
            var remote = github.get("/app/installations/" + installationId, signer.sign());
            if (remote.path("app_id").asLong() != appId || appId < 1) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Invalid App installation");
            installation = new GithubInstallation(installationId, text(remote.path("account"), "login", 100));
            installation.setActive(remote.path("suspended_at").isNull());
            installation = installations.save(installation);
        }
        if (!installation.isActive()) return receipt("INSTALLATION_INACTIVE");
        if (event.equals("installation_repositories")) {
            for (var removed : payload.path("repositories_removed")) {
                var repo = repositories.findByGithubRepositoryId(removed.path("id").asLong()).orElse(null);
                if (repo != null && repo.getInstallation().getId().equals(installation.getId())) { repo.deactivate(); grants.deleteByRepositoryId(repo.getId()); }
            }
            for (var added : payload.path("repositories_added")) upsertRepository(installation, added);
            return receipt("REPOSITORIES_UPDATED");
        }
        if (!Set.of("opened", "synchronize", "reopened", "closed").contains(action)) return receipt("IGNORED_ACTION");
        var knownRepo = repositories.findByGithubRepositoryId(payload.path("repository").path("id").asLong()).orElse(null);
        if (knownRepo != null && (!knownRepo.isActive() || !knownRepo.getInstallation().getId().equals(installation.getId())))
            return receipt("REPOSITORY_INACTIVE"); // Delayed PR events must not undo removal/transfer.
        var repo = upsertRepository(installation, payload.path("repository"));
        var remote = payload.path("pull_request");
        int number = payload.path("number").asInt();
        if (number < 1 || remote.path("number").asInt(number) != number) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Invalid pull request number");
        var pr = pullRequests.findByRepositoryIdAndNumber(repo.getId(), number).orElse(null);
        String sha = text(remote.path("head"), "sha", 64), title = text(remote, "title", 500), author = text(remote.path("user"), "login", 100);
        String headRef = text(remote.path("head"), "ref", 250), baseRef = text(remote.path("base"), "ref", 250), state = text(remote, "state", 20);
        if (pr == null) pr = new PullRequest(repo, number, title, author, sha, headRef, baseRef, state);
        else pr.refresh(title, author, sha, headRef, baseRef, state);
        pr = pullRequests.saveAndFlush(pr);
        for (var old : jobs.findByPullRequestIdOrderByCreatedAtDesc(pr.getId())) {
            if (!old.getCommitSha().equals(sha) || state.equals("closed")) jobs.lockById(old.getId()).ifPresent(job -> job.cancel(Instant.now()));
        }
        if (state.equals("closed")) return receipt("PULL_REQUEST_CLOSED");
        if (!repo.isReviewEnabled()) return receipt("REVIEW_DISABLED");
        var existing = jobs.findByPullRequestIdAndCommitSha(pr.getId(), sha);
        if (existing.isPresent()) return new Receipt("EXISTING_REVIEW", existing.get().getId(), true);
        var job = jobs.saveAndFlush(new ReviewJob(pr));
        outbox.save(new ReviewOutbox(job.getId(), ReviewOutbox.Kind.REVIEW, job.getAvailableAt()));
        return new Receipt("REVIEW_PENDING", job.getId(), false);
    }
    private CodeRepository upsertRepository(GithubInstallation installation, JsonNode remote) {
        long id = remote.path("id").asLong(); if (id < 1) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Repository ID is required");
        String name = text(remote, "full_name", 250); GithubClient.repositoryPath(name);
        var repo = repositories.findByGithubRepositoryId(id).orElse(null);
        String language = remote.path("language").isTextual() ? text(remote, "language", 100) : null;
        if (repo == null) repo = new CodeRepository(id, installation, name, remote.path("private").asBoolean(true), language);
        else {
            if (!repo.getInstallation().getId().equals(installation.getId())) { grants.deleteByRepositoryId(repo.getId()); grants.flush(); }
            repo.reconnect(installation); repo.updateMetadata(name, remote.path("private").asBoolean(true), language);
        }
        return repositories.save(repo);
    }
    private void lock(String key) { jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", key); }
    private Receipt receipt(String outcome) { return new Receipt(outcome, null, false); }
    private String text(JsonNode node, String key, int max) {
        String value = node.path(key).asText("");
        if (value.isBlank() || value.length() > max) throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Invalid pull request metadata");
        return value;
    }
}
