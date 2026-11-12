package com.codeguard.repository;

import com.codeguard.github.*;
import com.codeguard.user.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"codeguard.queue.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false", "spring.datasource.hikari.maximum-pool-size=3", "codeguard.github.app-id=42", "codeguard.github.webhook-secret=test-webhook-secret"})
@AutoConfigureMockMvc
@Transactional
class RepositoryAccessIT {
    static PostgreSQLContainer postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("CODEGUARD_TEST_DB_URL");
        if (url == null) {
            postgres = new PostgreSQLContainer("postgres:17-alpine"); postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("CODEGUARD_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("CODEGUARD_TEST_DB_PASSWORD"));
        }
    }
    @Autowired UserAccountRepository users;
    @Autowired InstallationRepository installations;
    @Autowired CodeRepositoryStore repositories;
    @Autowired RepositoryGrantStore grants;
    @Autowired MockMvc mvc;
    @Autowired com.codeguard.common.AuditEventStore audit;
    @Autowired com.codeguard.pullrequest.PullRequestStore pullRequests;
    @Autowired com.codeguard.review.ReviewJobStore jobs;
    @Autowired com.codeguard.review.ReviewResultStore results;
    @Autowired com.codeguard.analysis.ReviewFindingStore findings;
    @Autowired com.codeguard.ai.AiUsageStore usage;
    @Autowired GithubDiscovery discovery;
    @Autowired WebhookService webhooks;
    @org.springframework.test.context.bean.override.mockito.MockitoBean GithubClient github;
    @Autowired WebhookDeliveryStore deliveries;
    @Autowired com.codeguard.queue.ReviewOutboxStore outbox;
    @Autowired com.codeguard.review.ReviewWorker worker;
    @Autowired com.codeguard.review.ReviewExecution execution;
    @Autowired com.codeguard.ai.AiBudgetStore budgets;
    @org.springframework.test.context.bean.override.mockito.MockitoBean GithubPullRequests githubPullRequests;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.codeguard.ai.AIReviewProvider provider;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.codeguard.publishing.PublicationStore publications;
    @Autowired com.codeguard.publishing.PublicationService publishing;

    @Test void publishingRequiresWriterCsrfPolicyAndQueuesOnlyOnce() throws Exception {
        var writer = users.save(new UserAccount(7020, "publishing-writer", null));
        var reader = users.save(new UserAccount(7021, "publishing-reader", null));
        var outsider = users.save(new UserAccount(7022, "publishing-outsider", null));
        var job = workerJob(7020, true); var repo = job.getPullRequest().getRepository();
        grants.save(new RepositoryGrant(writer, repo, RepositoryGrant.Permission.WRITE, Instant.now().plusSeconds(300)));
        grants.save(new RepositoryGrant(reader, repo, RepositoryGrant.Permission.READ, Instant.now().plusSeconds(300)));
        worker.process(job.getId());
        String path = "/api/reviews/"+job.getId()+"/publication";
        mvc.perform(get(path).with(user(outsider.getId().toString()))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user(writer.getId().toString()))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(reader.getId().toString())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(writer.getId().toString())).with(csrf())).andExpect(status().isConflict());
        repo.updatePolicy(true, false, true, false);
        mvc.perform(get(path).with(user(writer.getId().toString()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.canPublish").value(true)).andExpect(jsonPath("$.preview.comments[0].line").value(1));
        mvc.perform(post(path).with(user(writer.getId().toString())).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        var id = publications.findByJobId(job.getId()).orElseThrow().getId();
        mvc.perform(post(path).with(user(writer.getId().toString())).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        assertThat(publications.findByJobId(job.getId()).orElseThrow().getId()).isEqualTo(id);
        repo.updatePolicy(true, false, false, false);
        assertThat(publishing.claim(id)).isNull();
        assertThat(publications.findById(id).orElseThrow().getStatus()).isEqualTo(com.codeguard.publishing.Publication.Status.CANCELLED);
    }

    @Test void automaticPublishingIsDurableAndAmbiguousOutcomesNeverResend() {
        var job = workerJob(7023, false); var repo = job.getPullRequest().getRepository();
        repo.updatePolicy(true, false, true, true);
        worker.process(job.getId());
        var publication = publications.findByJobId(job.getId()).orElseThrow();
        assertThat(publication.isAutomatic()).isTrue();
        var publisher = org.mockito.Mockito.mock(com.codeguard.publishing.GithubReviewPublisher.class);
        var payload = com.codeguard.publishing.PublicationPayload.create(publication.getId(), job.getCommitSha(), "Summary", java.util.List.of(), java.util.List.of());
        org.mockito.Mockito.when(publisher.prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(payload);
        org.mockito.Mockito.when(publisher.submitOnce(7023, "worker-team/repo", 1, payload)).thenThrow(new GithubApiException(502, true, 0));
        var publicationWorker = new com.codeguard.publishing.PublicationWorker(publications, publishing, publisher);
        publicationWorker.process(publication.getId()); publicationWorker.process(publication.getId());
        assertThat(publication.getStatus()).isEqualTo(com.codeguard.publishing.Publication.Status.UNKNOWN);
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1)).submitOnce(7023, "worker-team/repo", 1, payload);
        publishing.outcome(publication.getId(), 999L);
        publicationWorker.process(publication.getId());
        assertThat(publication.getStatus()).isEqualTo(com.codeguard.publishing.Publication.Status.PUBLISHED);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void publicationCommitsSendingBeforeExternalCallAndSurvivesDuplicateDispatch() {
        var job = transactions.execute(status -> {
            var created = workerJob(7025, false);
            created.getPullRequest().getRepository().updatePolicy(true, false, true, true);
            return created;
        });
        try {
            worker.process(job.getId());
            var publication = publications.findByJobId(job.getId()).orElseThrow();
            var publisher = org.mockito.Mockito.mock(com.codeguard.publishing.GithubReviewPublisher.class);
            var payload = com.codeguard.publishing.PublicationPayload.create(publication.getId(), job.getCommitSha(), "Summary", java.util.List.of(), java.util.List.of());
            org.mockito.Mockito.when(publisher.prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(payload);
            org.mockito.Mockito.when(publisher.submitOnce(7025, "worker-team/repo", 1, payload)).thenAnswer(invocation -> {
                assertThat(jdbc.queryForObject("select status from review_publications where id = ?", String.class, publication.getId())).isEqualTo("SENDING");
                assertThat(publishing.claim(publication.getId())).isNull();
                return 1234L;
            });
            var publicationWorker = new com.codeguard.publishing.PublicationWorker(publications, publishing, publisher);
            publicationWorker.process(publication.getId()); publicationWorker.process(publication.getId());
            assertThat(publications.findById(publication.getId()).orElseThrow().getStatus()).isEqualTo(com.codeguard.publishing.Publication.Status.PUBLISHED);
            org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1)).submitOnce(7025, "worker-team/repo", 1, payload);
        } finally {
            transactions.executeWithoutResult(status -> {
                var stored = jobs.findById(job.getId()).orElseThrow();
                var pr = stored.getPullRequest(); var repo = pr.getRepository(); var installation = repo.getInstallation();
                jdbc.update("delete from audit_events where resource_id = ?", job.getId());
                for (String table : java.util.List.of("review_publications", "review_results", "ai_usage_records", "ai_budget_reservations", "review_outbox"))
                    jdbc.update("delete from " + table + " where job_id = ?", job.getId());
                jdbc.update("delete from review_jobs where id = ?", job.getId());
                jdbc.update("delete from pull_requests where id = ?", pr.getId());
                jdbc.update("delete from repositories where id = ?", repo.getId());
                jdbc.update("delete from github_installations where id = ?", installation.getId());
            });
        }
    }

    @Test void publishingStopsAfterPermissionRevocationOrStaleHead() {
        var writer = users.save(new UserAccount(7024, "revoked-publisher", null));
        var job = workerJob(7024, false); var repo = job.getPullRequest().getRepository();
        repo.updatePolicy(true, false, true, false);
        var grant = grants.save(new RepositoryGrant(writer, repo, RepositoryGrant.Permission.WRITE, Instant.now().plusSeconds(300)));
        worker.process(job.getId());
        var requested = publishing.request(writer.getId(), job.getId());
        var claim = publishing.claim(requested.id());
        assertThat(claim).isNotNull(); assertThat(publishing.permitted(claim)).isTrue();
        grants.delete(grant); entityManager.flush();
        assertThat(publishing.permitted(claim)).isFalse();
        grants.save(new RepositoryGrant(writer, repo, RepositoryGrant.Permission.WRITE, Instant.now().plusSeconds(300)));
        job.getPullRequest().refresh("New commit", "dev", "b".repeat(40), "fix", "main", "open");
        assertThat(publishing.permitted(claim)).isFalse();
    }

    @Test void analyticsUsesStoredReviewsAndExcludesOtherUsersRepositories() throws Exception {
        var viewer = users.save(new UserAccount(7012, "analytics-viewer", null));
        var stranger = users.save(new UserAccount(7013, "analytics-stranger", null));
        var own = workerJob(7012, true); var hidden = workerJob(7013, true);
        grants.save(new RepositoryGrant(viewer, own.getPullRequest().getRepository(), RepositoryGrant.Permission.READ, Instant.now().plusSeconds(300)));
        grants.save(new RepositoryGrant(stranger, own.getPullRequest().getRepository(), RepositoryGrant.Permission.READ, Instant.now().minusSeconds(1)));
        worker.process(own.getId()); worker.process(hidden.getId());
        entityManager.flush();
        mvc.perform(get("/api/analytics").with(user(viewer.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totals.repositories").value(1))
            .andExpect(jsonPath("$.totals.reviews").value(1)).andExpect(jsonPath("$.totals.completed").value(1))
            .andExpect(jsonPath("$.totals.findings").value(1)).andExpect(jsonPath("$.severity[0].name").value("LOW"))
            .andExpect(jsonPath("$.recent[0].jobId").value(own.getId().toString()))
            .andExpect(jsonPath("$.usage.estimatedCostUsd").value(0));
        mvc.perform(get("/api/analytics").with(user(stranger.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totals.reviews").value(0))
            .andExpect(jsonPath("$.totals.repositories").value(0)).andExpect(jsonPath("$.recent").isEmpty());
        mvc.perform(get("/api/analytics").param("repositoryId", hidden.getPullRequest().getRepository().getId().toString()).with(user(viewer.getId().toString())))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/analytics").param("days", "400").with(user(viewer.getId().toString()))).andExpect(status().isBadRequest());
    }

    private com.codeguard.review.ReviewJob workerJob(long id, boolean privateRepository) {
        var installation = installations.save(new GithubInstallation(id, "worker-team"));
        var repo = repositories.save(new CodeRepository(id, installation, "worker-team/repo", privateRepository, "Java"));
        var pr = pullRequests.save(new com.codeguard.pullrequest.PullRequest(repo, 1, "Fix", "dev", "a".repeat(40), "fix", "main", "open"));
        var job = jobs.saveAndFlush(new com.codeguard.review.ReviewJob(pr));
        org.mockito.Mockito.when(githubPullRequests.snapshot(id, "worker-team/repo", 1, "a".repeat(40))).thenReturn(new GithubPullRequests.Snapshot("a".repeat(40), java.util.List.of(new GithubPullRequests.RawFile("src/App.java", "added", "@@ -0,0 +1 @@\n+error.printStackTrace();\n", false)), 1, false));
        return job;
    }
    @Test void workerPersistsMockReviewStaticFindingsAndUsageExactlyOnce() {
        var job = workerJob(7001, true);
        worker.process(job.getId()); worker.process(job.getId());
        assertThat(job.getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.COMPLETED);
        assertThat(job.getAttempts()).isEqualTo(1);
        var result = results.findByJobId(job.getId()).orElseThrow();
        assertThat(result.getProvider()).isEqualTo("mock");
        assertThat(result.getSummary()).contains("No cloud AI analysis");
        assertThat(findings.findByResultIdOrderByCreatedAtAsc(result.getId())).hasSize(1);
        assertThat(usage.findByJobIdOrderByCreatedAtAsc(job.getId())).hasSize(1);
        assertThat(budgets.totalReserved(job.getId())).isEqualByComparingTo("0");
        org.mockito.Mockito.verify(githubPullRequests, org.mockito.Mockito.times(1)).snapshot(7001, "worker-team/repo", 1, "a".repeat(40));
    }
    @Test void workerKeepsPrivateReviewLocalWithoutConsent() {
        var job = workerJob(7002, true);
        org.mockito.Mockito.doReturn(true).when(provider).external();
        worker.process(job.getId());
        var result = results.findByJobId(job.getId()).orElseThrow();
        assertThat(result.getProvider()).isEqualTo("static");
        assertThat(result.getSummary()).contains("consent");
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.never()).review(org.mockito.ArgumentMatchers.any());
        assertThat(usage.findByJobIdOrderByCreatedAtAsc(job.getId())).isEmpty();
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void workerCommitsAcrossSeparateTransactionsAndSurvivesRedelivery() {
        var job = transactions.execute(status -> workerJob(7010, true));
        try {
            worker.process(job.getId()); worker.process(job.getId());
            assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.COMPLETED);
            assertThat(results.findByJobId(job.getId())).isPresent();
            assertThat(usage.findByJobIdOrderByCreatedAtAsc(job.getId())).hasSize(1);
            assertThat(jobs.findById(job.getId()).orElseThrow().getAttempts()).isEqualTo(1);
        } finally {
            transactions.executeWithoutResult(status -> {
                var stored = jobs.findById(job.getId()).orElseThrow();
                var pr = stored.getPullRequest(); var repo = pr.getRepository(); var installation = repo.getInstallation();
                for (String table : java.util.List.of("review_results", "ai_usage_records", "ai_budget_reservations", "review_outbox"))
                    jdbc.update("delete from " + table + " where job_id = ?", job.getId());
                jdbc.update("delete from review_jobs where id = ?", job.getId());
                jdbc.update("delete from pull_requests where id = ?", pr.getId());
                jdbc.update("delete from repositories where id = ?", repo.getId());
                jdbc.update("delete from github_installations where id = ?", installation.getId());
            });
        }
    }
    @Test void expiredWorkerIsFencedAndRecoveryRearmsDelivery() {
        var job = workerJob(7011, false);
        var claim = execution.claim(job.getId());
        org.springframework.test.util.ReflectionTestUtils.setField(job, "leaseUntil", Instant.now().minusSeconds(1));
        execution.recover(job.getId());
        assertThat(job.getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.PENDING);
        assertThat(outbox.findByJobIdAndKind(job.getId(), com.codeguard.queue.ReviewOutbox.Kind.REVIEW)).isPresent();
        var empty = new com.codeguard.analysis.DiffNormalizer.Normalized(java.util.List.of(), 0, false);
        assertThat(execution.complete(claim, empty, "mock", "stale worker", java.util.List.of())).isFalse();
        assertThat(results.findByJobId(job.getId())).isEmpty();
    }
    @Test void workerRetriesThroughOutboxAndCancelsStalePullRequests() {
        var job = workerJob(7003, false);
        org.mockito.Mockito.when(githubPullRequests.snapshot(7003, "worker-team/repo", 1, "a".repeat(40))).thenThrow(new GithubApiException(429, true, 60));
        worker.process(job.getId());
        assertThat(job.getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.PENDING);
        assertThat(job.getAvailableAt()).isAfter(Instant.now().plusSeconds(50));
        assertThat(outbox.findByJobIdAndKind(job.getId(), com.codeguard.queue.ReviewOutbox.Kind.REVIEW)).isPresent();
        worker.process(job.getId()); assertThat(job.getAttempts()).isEqualTo(1);
        var stale = workerJob(7004, false);
        org.mockito.Mockito.when(githubPullRequests.snapshot(7004, "worker-team/repo", 1, "a".repeat(40))).thenThrow(new StalePullRequestException());
        worker.process(stale.getId());
        assertThat(stale.getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.CANCELLED);
    }
    @Test void durableReservationsBoundCostsAcrossRetriesAndFailureCreatesDeadLetter() {
        var job = workerJob(7005, false);
        org.mockito.Mockito.doReturn(new java.math.BigDecimal("0.03")).when(provider).reservation(org.mockito.ArgumentMatchers.any());
        var first = execution.claim(job.getId());
        var files = java.util.List.of(new com.codeguard.analysis.ChangedFile("a.java", "added", "@@ -0,0 +1 @@\n+x\n", java.util.Set.of(1), false));
        execution.prepare(first, files);
        execution.fail(first, "AI_UNAVAILABLE", true, 0);
        org.springframework.test.util.ReflectionTestUtils.setField(job, "availableAt", Instant.now().minusSeconds(1));
        var second = execution.claim(job.getId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> execution.prepare(second, files)).hasMessage("AI_REVIEW_COST_BUDGET");
        assertThat(budgets.totalReserved(job.getId())).isEqualByComparingTo("0.03");
        // The thrown exception marks this test transaction rollback-only, but state remains inspectable.
        execution.fail(second, "AI_REVIEW_COST_BUDGET", false, 0);
        assertThat(job.getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.FAILED);
        assertThat(outbox.findByJobIdAndKind(job.getId(), com.codeguard.queue.ReviewOutbox.Kind.DEAD_LETTER)).isPresent();
    }

    private String signature(String body) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-webhook-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(String id, String event, String body) throws Exception {
        return post("/api/webhooks/github").header("X-GitHub-Delivery", id).header("X-GitHub-Event", event)
            .header("X-Hub-Signature-256", signature(body)).contentType("application/json").content(body);
    }
    @Test void verifiedWebhooksDeduplicateDeliveriesAndCommitsAndCreateDurableOutbox() throws Exception {
        installations.saveAndFlush(new GithubInstallation(2060, "team"));
        String body = """
            {"action":"opened","number":12,"installation":{"id":2060},
             "repository":{"id":3060,"full_name":"team/webhooks","private":true},
             "pull_request":{"number":12,"title":"Fix validation","state":"open","user":{"login":"dev"},
               "head":{"sha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","ref":"fix"},"base":{"ref":"main"}}}
            """;
        String delivery = java.util.UUID.randomUUID().toString();
        mvc.perform(webhook(delivery, "pull_request", body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.outcome").value("REVIEW_PENDING"));
        mvc.perform(webhook(delivery, "pull_request", body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.duplicate").value(true));
        mvc.perform(webhook(java.util.UUID.randomUUID().toString(), "pull_request", body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.outcome").value("EXISTING_REVIEW"));
        assertThat(deliveries.count()).isEqualTo(2); assertThat(jobs.count()).isEqualTo(1); assertThat(outbox.count()).isEqualTo(1);
        mvc.perform(webhook(delivery, "pull_request", body.replace("Fix validation", "Different payload"))).andExpect(status().isConflict());
        mvc.perform(post("/api/webhooks/github").header("X-Hub-Signature-256", "sha256=" + "0".repeat(64)).content("{malformed"))
            .andExpect(status().isUnauthorized());
        assertThat(deliveries.count()).isEqualTo(2);
        String removal = "{\"action\":\"removed\",\"installation\":{\"id\":2060},\"repositories_removed\":[{\"id\":3060}]}";
        mvc.perform(webhook(java.util.UUID.randomUUID().toString(), "installation_repositories", removal)).andExpect(status().isAccepted());
        mvc.perform(webhook(java.util.UUID.randomUUID().toString(), "pull_request", body.replace("opened", "synchronize")))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.outcome").value("REPOSITORY_INACTIVE"));
        assertThat(repositories.findByGithubRepositoryId(3060).orElseThrow().isActive()).isFalse();
    }
    @Test void oversizedWebhookIsRejectedBeforeProcessing() throws Exception {
        mvc.perform(post("/api/webhooks/github").content(new byte[2_000_001])).andExpect(status().isPayloadTooLarge());
        assertThat(deliveries.count()).isZero();
    }
    @Test void demoSignInDoesNotExistWhenDemoModeIsDisabled() throws Exception {
        mvc.perform(post("/api/auth/demo").with(csrf())).andExpect(status().isNotFound());
    }

    @Test void discoveryRefreshesUserSpecificGrantsWithoutGrantingInstallationWideAccess() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        org.mockito.Mockito.when(github.get("/user", "user-token")).thenReturn(json.readTree("{\"id\":1050,\"login\":\"octocat\"}"));
        org.mockito.Mockito.when(github.list("/user/installations", "installations", "user-token")).thenReturn(java.util.List.of(
            json.readTree("{\"id\":2050,\"app_id\":42,\"suspended_at\":null,\"account\":{\"login\":\"team\"}}")));
        org.mockito.Mockito.when(github.list("/user/installations/2050/repositories", "repositories", "user-token")).thenReturn(java.util.List.of(
            json.readTree("{\"id\":3050,\"full_name\":\"team/allowed\",\"private\":true,\"permissions\":{\"pull\":true,\"push\":false}}"),
            json.readTree("{\"id\":3051,\"full_name\":\"team/denied\",\"permissions\":{\"pull\":false}}")));
        var first = discovery.synchronize("user-token");
        assertThat(first.repositories()).isEqualTo(1);
        mvc.perform(get("/api/installations").with(user(first.userId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].account").value("team"));
        assertThat(repositories.visibleTo(first.userId(), Instant.now(), "", PageRequest.of(0, 20))).hasSize(1);
        assertThat(repositories.findByGithubRepositoryId(3051)).isEmpty();
        var allowed = repositories.findByGithubRepositoryId(3050).orElseThrow();
        assertThat(grants.validGrant(first.userId(), allowed.getId(), Instant.now()).orElseThrow().canWrite()).isFalse();
        org.mockito.Mockito.when(github.list("/user/installations/2050/repositories", "repositories", "user-token")).thenReturn(java.util.List.of());
        discovery.synchronize("user-token");
        assertThat(grants.validGrant(first.userId(), allowed.getId(), Instant.now())).isEmpty();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"installation", "github_app_authorization"})
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void staleDiscoveryCannotUndoCommittedAccessRevocation(String event) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        org.mockito.Mockito.when(github.get("/user", "race-token")).thenReturn(json.readTree("{\"id\":1052,\"login\":\"race-user\"}"));
        org.mockito.Mockito.when(github.list("/user/installations", "installations", "race-token")).thenReturn(java.util.List.of(
            json.readTree("{\"id\":2052,\"app_id\":42,\"suspended_at\":null,\"account\":{\"login\":\"race-team\"}}")));
        var snapshot = java.util.List.of(json.readTree("{\"id\":3052,\"full_name\":\"race-team/repo\",\"private\":true,\"permissions\":{\"pull\":true,\"push\":true}}"));
        org.mockito.Mockito.when(github.list("/user/installations/2052/repositories", "repositories", "race-token")).thenReturn(snapshot);
        var initial = discovery.synchronize("race-token");
        var deliveryId = java.util.UUID.randomUUID().toString();
        try {
            var repoId = repositories.findByGithubRepositoryId(3052).orElseThrow().getId();
            String body = event.equals("installation")
                ? "{\"action\":\"deleted\",\"installation\":{\"id\":2052,\"app_id\":42,\"account\":{\"login\":\"race-team\"}}}"
                : "{\"action\":\"revoked\",\"sender\":{\"id\":1052}}";
            org.mockito.Mockito.when(github.list("/user/installations/2052/repositories", "repositories", "race-token")).thenAnswer(call -> {
                // The already-fetched installation/repository snapshot is now stale, while
                // the signed access-changing webhook commits in a separate transaction.
                webhooks.processVerified(deliveryId, event, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return snapshot;
            });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> discovery.synchronize("race-token"))
                .isInstanceOfSatisfying(GithubApiException.class, failure -> assertThat(failure.status()).isEqualTo(409));
            assertThat(grants.validGrant(initial.userId(), repoId, Instant.now())).isEmpty();
            if (event.equals("installation")) assertThat(installations.findByGithubInstallationId(2052).orElseThrow().isActive()).isFalse();
            else assertThat(grants.findByUserIdAndRepositoryId(initial.userId(), repoId)).isEmpty();
            assertThat(deliveries.findByDeliveryId(deliveryId)).isPresent();
        } finally {
            transactions.executeWithoutResult(status -> {
                grants.deleteByUserId(initial.userId()); grants.flush();
                repositories.findByGithubRepositoryId(3052).ifPresent(repositories::delete); repositories.flush();
                installations.findByGithubInstallationId(2052).ifPresent(installations::delete); installations.flush();
                users.deleteById(initial.userId());
                deliveries.findByDeliveryId(deliveryId).ifPresent(deliveries::delete);
            });
        }
    }

    @Test void generatedOpenApiIncludesProtectedEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs").with(user("documentation-viewer"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/repositories']").exists())
            .andExpect(jsonPath("$.paths['/api/webhooks/github']").exists());
    }

    @Test void storedReviewResultsAreVisibleOnlyThroughValidRepositoryGrants() throws Exception {
        var viewer = users.save(new UserAccount(1010, "viewer", null));
        var stranger = users.save(new UserAccount(1011, "stranger", null));
        var installation = installations.save(new GithubInstallation(2010, "team"));
        var repo = repositories.save(new CodeRepository(3010, installation, "team/results", true, "Java"));
        grants.save(new RepositoryGrant(viewer, repo, RepositoryGrant.Permission.READ, Instant.now().plusSeconds(300)));
        var pr = pullRequests.save(new com.codeguard.pullrequest.PullRequest(repo, 9, "Resource cleanup", "dev", "d".repeat(40), "fix", "main", "open"));
        var job = jobs.save(new com.codeguard.review.ReviewJob(pr));
        var now = job.getAvailableAt(); job.complete(job.claim(now, java.time.Duration.ofMinutes(1)), now.plusSeconds(1));
        var result = results.save(new com.codeguard.review.ReviewResult(job, "mock", "One advisory finding", "[]", 1, true));
        var data = new com.codeguard.analysis.FindingData("Close the stream", "A stream remains open", "src/Main.java", 4,
            com.codeguard.analysis.FindingData.Severity.MEDIUM, com.codeguard.analysis.FindingData.Category.RESOURCE_LEAK,
            "Use try-with-resources", 0.9, "The resource must be closed");
        findings.save(new com.codeguard.analysis.ReviewFinding(result, data));
        usage.save(new com.codeguard.ai.AiUsage(job.getId(), 1, "mock", "deterministic", 0, 0, false, java.math.BigDecimal.ZERO));
        mvc.perform(get("/api/reviews/" + job.getId()).with(user(viewer.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.job.status").value("COMPLETED"))
            .andExpect(jsonPath("$.findings[0].data.title").value("Close the stream"))
            .andExpect(jsonPath("$.incomplete").value(true)).andExpect(jsonPath("$.usage[0].measuredTokens").value(false));
        mvc.perform(get("/api/reviews/" + job.getId()).with(user(stranger.getId().toString()))).andExpect(status().isNotFound());
    }

    @Test void commitReviewsAreUniqueAndKeepTheirOriginalHead() {
        var installation = installations.save(new GithubInstallation(2004, "team"));
        var repo = repositories.save(new CodeRepository(3004, installation, "team/reviews", false, "Java"));
        var pr = pullRequests.save(new com.codeguard.pullrequest.PullRequest(repo, 7, "Fix", "dev", "a".repeat(40), "fix", "main", "open"));
        var first = jobs.saveAndFlush(new com.codeguard.review.ReviewJob(pr));
        pr.refresh("Fix again", "dev", "b".repeat(40), "fix", "main", "open");
        var second = jobs.saveAndFlush(new com.codeguard.review.ReviewJob(pr));
        assertThat(first.getCommitSha()).isEqualTo("a".repeat(40));
        assertThat(second.getCommitSha()).isEqualTo("b".repeat(40));
        assertThat(jobs.findByPullRequestIdOrderByCreatedAtDesc(pr.getId())).hasSize(2);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> jobs.saveAndFlush(new com.codeguard.review.ReviewJob(pr)));
    }

    @Test void repositoryVisibilityRequiresUnexpiredGrantAndActiveInstallation() {
        var user = users.save(new UserAccount(1001, "reader", null));
        var other = users.save(new UserAccount(1002, "outsider", null));
        var installation = installations.save(new GithubInstallation(2001, "team"));
        var repo = repositories.save(new CodeRepository(3001, installation, "team/service", true, "Java"));
        var grant = grants.save(new RepositoryGrant(user, repo, RepositoryGrant.Permission.READ, Instant.now().plusSeconds(300)));
        assertThat(repositories.visibleTo(user.getId(), Instant.now(), "service", PageRequest.of(0, 20)).getContent()).containsExactly(repo);
        assertThat(repositories.visibleTo(other.getId(), Instant.now(), "", PageRequest.of(0, 20))).isEmpty();
        assertThat(grants.validGrant(user.getId(), repo.getId(), Instant.now()).orElseThrow().canWrite()).isFalse();
        grant.refresh(RepositoryGrant.Permission.READ, Instant.now().minusSeconds(1));
        grants.flush();
        assertThat(grants.validGrant(user.getId(), repo.getId(), Instant.now())).isEmpty();
        grant.refresh(RepositoryGrant.Permission.ADMIN, Instant.now().plusSeconds(300));
        installation.setActive(false); installations.flush();
        assertThat(repositories.visibleTo(user.getId(), Instant.now(), "", PageRequest.of(0, 20))).isEmpty();
        assertThat(grants.validGrant(user.getId(), repo.getId(), Instant.now())).isEmpty();
    }

    @Test void pullRequestHistoryAndCancellationRespectRepositoryBoundaries() throws Exception {
        var writer = users.save(new UserAccount(1006, "writer", null));
        var outsider = users.save(new UserAccount(1007, "outsider", null));
        var installation = installations.save(new GithubInstallation(2005, "team"));
        var repo = repositories.save(new CodeRepository(3005, installation, "team/prs", false, "Java"));
        grants.save(new RepositoryGrant(writer, repo, RepositoryGrant.Permission.WRITE, Instant.now().plusSeconds(300)));
        var pr = pullRequests.save(new com.codeguard.pullrequest.PullRequest(repo, 8, "Fix null handling", "dev", "c".repeat(40), "fix", "main", "open"));
        var job = jobs.saveAndFlush(new com.codeguard.review.ReviewJob(pr));
        mvc.perform(get("/api/pull-requests?state=open&search=null").with(user(writer.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].number").value(8));
        mvc.perform(get("/api/pull-requests?repositoryId=" + repo.getId()).with(user(writer.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/pull-requests/" + pr.getId()).with(user(writer.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reviews[0].status").value("PENDING"));
        mvc.perform(get("/api/pull-requests/" + pr.getId()).with(user(outsider.getId().toString())))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/reviews/" + job.getId() + "/cancel").with(user(outsider.getId().toString())).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/reviews/" + job.getId() + "/cancel").with(user(writer.getId().toString())).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(com.codeguard.review.ReviewStatus.CANCELLED);
    }

    @Test void apiEnforcesAuthorizationCsrfValidationAndAuditsPolicyChanges() throws Exception {
        var owner = users.save(new UserAccount(1003, "owner", null));
        var reader = users.save(new UserAccount(1004, "reader", null));
        var outsider = users.save(new UserAccount(1005, "outsider", null));
        var installation = installations.save(new GithubInstallation(2003, "team"));
        var repo = repositories.save(new CodeRepository(3003, installation, "team/api", true, "Java"));
        grants.save(new RepositoryGrant(owner, repo, RepositoryGrant.Permission.ADMIN, Instant.now().plusSeconds(300)));
        grants.save(new RepositoryGrant(reader, repo, RepositoryGrant.Permission.READ, Instant.now().plusSeconds(300)));
        var ownerSession = user(owner.getId().toString());
        String path = "/api/repositories/" + repo.getId();
        String policy = "{\"reviewEnabled\":true,\"cloudAiOptIn\":true,\"publishingEnabled\":true,\"autoPublish\":false}";
        mvc.perform(get("/api/repositories").with(ownerSession)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].fullName").value("team/api"));
        mvc.perform(get(path).with(user(outsider.getId().toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/repositories").with(user(outsider.getId().toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/repositories?size=101").with(ownerSession)).andExpect(status().isBadRequest());
        mvc.perform(patch(path + "/policy").with(ownerSession).contentType("application/json").content(policy))
            .andExpect(status().isForbidden());
        mvc.perform(patch(path + "/policy").with(user(reader.getId().toString())).with(csrf()).contentType("application/json").content(policy))
            .andExpect(status().isForbidden());
        assertThat(audit.count()).isZero();
        mvc.perform(patch(path + "/policy").with(ownerSession).with(csrf()).contentType("application/json")
            .content("{\"publishingEnabled\":false,\"autoPublish\":true}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch(path + "/policy").with(ownerSession).with(csrf()).contentType("application/json").content(policy))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cloudAiOptIn").value(true));
        assertThat(audit.count()).isEqualTo(1);
        assertThat(repositories.findById(repo.getId()).orElseThrow().isCloudAiOptIn()).isTrue();
        mvc.perform(get("/api/me").with(ownerSession)).andExpect(status().isOk()).andExpect(jsonPath("$.login").value("owner"));
    }

    @Test void cloudConsentAndPublishingAreOffByDefault() {
        var installation = installations.save(new GithubInstallation(2002, "team"));
        var repo = repositories.saveAndFlush(new CodeRepository(3002, installation, "team/private", true, "Java"));
        assertThat(repo.isCloudAiOptIn()).isFalse();
        assertThat(repo.isPublishingEnabled()).isFalse();
        assertThat(repo.isAutoPublish()).isFalse();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> repo.updatePolicy(true, false, false, true));
    }
}
