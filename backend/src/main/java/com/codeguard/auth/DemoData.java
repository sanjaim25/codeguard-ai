package com.codeguard.auth;

import com.codeguard.ai.*;
import com.codeguard.analysis.*;
import com.codeguard.github.*;
import com.codeguard.pullrequest.*;
import com.codeguard.repository.*;
import com.codeguard.review.*;
import com.codeguard.user.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit local demo fixtures. Negative GitHub identifiers can never identify live GitHub resources. */
@Service
@ConditionalOnProperty(name = "codeguard.demo", havingValue = "true")
public class DemoData {
    public static final long USER_ID = -1000;
    private final UserAccountRepository users;
    private final InstallationRepository installations;
    private final CodeRepositoryStore repositories;
    private final RepositoryGrantStore grants;
    private final PullRequestStore prs;
    private final ReviewJobStore jobs;
    private final ReviewResultStore results;
    private final ReviewFindingStore findings;
    private final AiUsageStore usage;
    private final DiffNormalizer normalizer;
    private final StaticChecks checks;
    private final ObjectMapper json;
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public DemoData(UserAccountRepository users, InstallationRepository installations, CodeRepositoryStore repositories,
        RepositoryGrantStore grants, PullRequestStore prs, ReviewJobStore jobs, ReviewResultStore results, ReviewFindingStore findings,
        AiUsageStore usage, DiffNormalizer normalizer, StaticChecks checks, ObjectMapper json, EntityManager entities, JdbcTemplate jdbc, Clock clock,
        AiSettings settings, @Value("${codeguard.github.auth-enabled:false}") boolean githubAuth) {
        if (githubAuth || !settings.provider().equals("mock")) throw new IllegalStateException("Demo mode requires mock AI and disabled GitHub OAuth; use a separate database for live mode");
        this.users = users; this.installations = installations; this.repositories = repositories; this.grants = grants;
        this.prs = prs; this.jobs = jobs; this.results = results; this.findings = findings; this.usage = usage;
        this.normalizer = normalizer; this.checks = checks; this.json = json; this.entities = entities; this.jdbc = jdbc; this.clock = clock;
    }
    @Transactional
    public UserAccount signIn() {
        jdbc.execute("select pg_advisory_xact_lock(90878812)");
        var user = users.findByGithubUserId(USER_ID).orElseGet(() -> users.save(new UserAccount(USER_ID, "demo-developer", null)));
        var installation = installations.findByGithubInstallationId(-1000).orElseGet(() -> installations.save(new GithubInstallation(-1000, "demo-workspace")));
        String[] names = {"demo/payment-service", "demo/identity-api", "demo/web-console"};
        for (int i = 0; i < names.length; i++) {
            long githubId = -1001 - i; String name = names[i]; int index = i;
            var repo = repositories.findByGithubRepositoryId(githubId).orElse(null);
            if (repo == null) {
                repo = repositories.save(new CodeRepository(githubId, installation, name, true, index == 2 ? "TypeScript" : "Java"));
                seed(repo, index * 2, true); seed(repo, index * 2 + 1, false);
            }
            var current = grants.findByUserIdAndRepositoryId(user.getId(), repo.getId()).orElse(null);
            if (current == null) grants.save(new RepositoryGrant(user, repo, RepositoryGrant.Permission.WRITE, clock.instant().plus(Duration.ofHours(8))));
            else current.refresh(RepositoryGrant.Permission.WRITE, clock.instant().plus(Duration.ofHours(8)));
            entities.flush();
            refreshFixtureDates(repo, index * 2);
            refreshFixtureDates(repo, index * 2 + 1);
        }
        entities.flush();
        return user;
    }
    private void seed(CodeRepository repo, int index, boolean concern) {
        String sha = String.format("%040x", index + 1);
        String title = concern ? (index == 4 ? "Validate report expressions" : "Add payment reconciliation job") : "Tighten request validation";
        String path = index == 4 ? "src/reports.ts" : "src/main/java/demo/PaymentService.java";
        String patch = index == 4 ? "@@ -0,0 +1,3 @@\n+export function calculate(expression: string) {\n+  return eval(expression);\n+}\n"
            : concern ? "@@ -1,3 +1,7 @@\n public void reconcile(String command) {\n-    queue.submit(command);\n+    try {\n+        Runtime.getRuntime().exec(command);\n+    } catch (IOException error) {\n+        error.printStackTrace();\n+    }\n }\n"
            : "@@ -1,3 +1,6 @@\n public String normalize(String value) {\n-    return value.trim();\n+    if (value == null || value.isBlank()) {\n+        throw new IllegalArgumentException(\"Value is required\");\n+    }\n+    return value.strip();\n }\n";
        var pr = prs.save(new PullRequest(repo, 42 + index, title, "demo-developer", sha, "feature/review-" + index, "main", "open"));
        var job = jobs.save(new ReviewJob(pr));
        Instant now = clock.instant().plusSeconds(1);
        UUID claim = job.claim(now, Duration.ofMinutes(1));
        var diff = normalizer.normalize(new GithubPullRequests.Snapshot(sha, List.of(new GithubPullRequests.RawFile(path, "modified", patch, false)), 1, false));
        var output = new MockAIReviewProvider().review(new AIReviewProvider.Input(diff.files(), true, false));
        String diffJson;
        try { diffJson = json.writeValueAsString(diff.files()); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("Demo fixture could not be serialized"); }
        var result = results.save(new ReviewResult(job, "mock", "DEMO FIXTURE — " + output.summary(), diffJson, diff.excludedFiles(), diff.incomplete()));
        findings.saveAll(checks.analyze(diff.files()).stream().map(finding -> new ReviewFinding(result, finding)).toList());
        usage.save(new AiUsage(job.getId(), 1, "mock", "mock", 0, 0, false, java.math.BigDecimal.ZERO));
        job.complete(claim, now.plusSeconds(2));
        entities.flush();
    }
    private void refreshFixtureDates(CodeRepository repo, int index) {
        // Explicit demo-only fixtures remain in the walkthrough's 30-day window after a long pause.
        // Exact repository, PR number and synthetic SHA prevent changing any unrelated history.
        Instant historical = clock.instant().minus(Duration.ofDays(index * 4L)).minusSeconds(60);
        jdbc.update("""
            update review_jobs j set created_at=?, updated_at=?, started_at=?, completed_at=?
            from pull_requests p where j.pull_request_id=p.id and p.repository_id=? and p.number=?
            and j.commit_sha=? and j.status='COMPLETED'
            """, java.sql.Timestamp.from(historical), java.sql.Timestamp.from(historical.plusSeconds(3)),
            java.sql.Timestamp.from(historical.plusSeconds(1)), java.sql.Timestamp.from(historical.plusSeconds(3)),
            repo.getId(), 42 + index, String.format("%040x", index + 1));
    }
}
