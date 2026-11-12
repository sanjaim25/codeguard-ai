package com.codeguard.github;

import com.codeguard.auth.AuthenticationExpiredException;
import com.codeguard.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.*;

@RestController
public class GithubAccountController {
    private final GithubDiscovery discovery;
    private final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClients;
    private final boolean enabled;
    private final String appSlug;
    private final CurrentUser users;
    private final InstallationRepository installations;
    public GithubAccountController(GithubDiscovery discovery, ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClients,
                                   @Value("${codeguard.github.auth-enabled:false}") boolean enabled,
                                   @Value("${codeguard.github.app-slug:}") String appSlug, CurrentUser users, InstallationRepository installations) {
        this.discovery = discovery; this.authorizedClients = authorizedClients; this.enabled = enabled; this.appSlug = appSlug;
        this.users = users; this.installations = installations;
    }
    public record Installation(UUID id, long githubInstallationId, String account) {}
    @GetMapping("/api/installations")
    public List<Installation> installations(Authentication authentication) {
        return installations.visibleTo(users.require(authentication).getId(), Instant.now()).stream()
            .map(i -> new Installation(i.getId(), i.getGithubInstallationId(), i.getAccountLogin())).toList();
    }
    @GetMapping("/api/auth/config")
    public Map<String, Object> configuration() {
        return Map.of("githubEnabled", enabled, "installationUrl", appSlug.matches("[a-z0-9-]+") ? "https://github.com/apps/" + appSlug + "/installations/new" : "");
    }
    @PostMapping("/api/github/sync")
    public GithubDiscovery.Synced sync(Authentication authentication, HttpServletRequest request) {
        var repository = authorizedClients.getIfAvailable();
        if (repository == null) throw new AuthenticationExpiredException();
        OAuth2AuthorizedClient client = repository.loadAuthorizedClient("github", authentication, request);
        if (client == null || (client.getAccessToken().getExpiresAt() != null && !client.getAccessToken().getExpiresAt().isAfter(Instant.now().plusSeconds(30))))
            throw new AuthenticationExpiredException();
        try { return discovery.synchronize(client.getAccessToken().getTokenValue()); }
        catch (GithubApiException failure) { if (failure.status() == 401) throw new AuthenticationExpiredException(); throw failure; }
    }
}
