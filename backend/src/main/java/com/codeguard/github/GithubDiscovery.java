package com.codeguard.github;

import com.codeguard.repository.*;
import com.codeguard.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GithubDiscovery {
    private record Discovered(JsonNode installation, List<JsonNode> repositories) {}
    public record Synced(UUID userId, int installations, int repositories, Instant accessExpiresAt) {}
    private final GithubClient github;
    private final UserAccountRepository users;
    private final InstallationRepository installations;
    private final CodeRepositoryStore repositories;
    private final RepositoryGrantStore grants;
    private final TransactionTemplate transactions;
    private final GithubAccessFence accessFence;
    private final Clock clock;
    private final long appId;
    public GithubDiscovery(GithubClient github, UserAccountRepository users, InstallationRepository installations, CodeRepositoryStore repositories,
                           RepositoryGrantStore grants, TransactionTemplate transactions, GithubAccessFence accessFence, Clock clock,
                           @Value("${codeguard.github.app-id:0}") String appId) {
        this.github = github; this.users = users; this.installations = installations; this.repositories = repositories;
        this.grants = grants; this.transactions = transactions; this.accessFence = accessFence; this.clock = clock;
        this.appId = appId.isBlank() ? 0 : Long.parseLong(appId);
    }
    /** Fetch a complete authorization snapshot before modifying any grants. Network errors never grant partial access. */
    public Synced synchronize(String userToken) {
        long revision = accessFence.revision();
        JsonNode profile = github.get("/user", userToken);
        if (profile.path("id").asLong() < 1) throw new GithubApiException(502, false, 0);
        List<Discovered> discovered = new ArrayList<>();
        for (var installation : github.list("/user/installations", "installations", userToken)) {
            if (installation.path("app_id").asLong() != appId || !installation.path("suspended_at").isNull()) continue;
            long id = installation.path("id").asLong();
            if (id < 1) throw new GithubApiException(502, false, 0);
            discovered.add(new Discovered(installation, github.list("/user/installations/" + id + "/repositories", "repositories", userToken)));
        }
        return transactions.execute(status -> persist(profile, discovered, revision));
    }
    private Synced persist(JsonNode profile, List<Discovered> discovered, long revision) {
        accessFence.lock();
        accessFence.requireUnchanged(revision);
        var user = users.findByGithubUserId(profile.path("id").asLong()).orElseGet(() -> new UserAccount(profile.path("id").asLong(), "", null));
        String avatar = profile.path("avatar_url").asText("");
        if (!avatar.startsWith("https://avatars.githubusercontent.com/")) avatar = null;
        user.updateProfile(text(profile, "login", 100), avatar);
        user = users.saveAndFlush(user);
        grants.deleteByUserId(user.getId()); grants.flush();
        Instant expires = clock.instant().plusSeconds(600);
        int count = 0;
        for (var item : discovered) {
            long id = item.installation().path("id").asLong();
            var installation = installations.findByGithubInstallationId(id).orElseGet(() ->
                new GithubInstallation(id, text(item.installation().path("account"), "login", 100)));
            installation.setActive(true); installation = installations.save(installation);
            for (var remote : item.repositories()) {
                if (!remote.path("permissions").path("pull").asBoolean(false)) continue;
                long repoId = remote.path("id").asLong();
                if (repoId < 1) throw new GithubApiException(502, false, 0);
                String fullName = text(remote, "full_name", 250); GithubClient.repositoryPath(fullName);
                var repo = repositories.findByGithubRepositoryId(repoId).orElse(null);
                String language = remote.path("language").isTextual() ? text(remote, "language", 100) : null;
                if (repo == null) repo = new CodeRepository(repoId, installation, fullName, remote.path("private").asBoolean(true), language);
                else {
                    if (!repo.getInstallation().getId().equals(installation.getId())) { grants.deleteByRepositoryId(repo.getId()); grants.flush(); }
                    repo.reconnect(installation); repo.updateMetadata(fullName, remote.path("private").asBoolean(true), language);
                }
                repo = repositories.save(repo);
                var permission = remote.path("permissions").path("admin").asBoolean() ? RepositoryGrant.Permission.ADMIN :
                    remote.path("permissions").path("push").asBoolean() ? RepositoryGrant.Permission.WRITE : RepositoryGrant.Permission.READ;
                grants.save(new RepositoryGrant(user, repo, permission, expires)); count++;
            }
        }
        accessFence.changed();
        return new Synced(user.getId(), discovered.size(), count, expires);
    }
    static String text(JsonNode node, String field, int max) {
        String value = node.path(field).asText("");
        if (value.isBlank() || value.length() > max) throw new GithubApiException(502, false, 0);
        return value;
    }
}
