package com.codeguard.github;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InstallationTokens {
    private record Token(String value, Instant expiresAt) {
        @Override public String toString() { return "[redacted installation token]"; }
    }
    private final Map<Long, Token> cache = new HashMap<>();
    private final GithubClient github;
    private final AppJwtSigner signer;
    private final Clock clock;
    public InstallationTokens(GithubClient github, AppJwtSigner signer, Clock clock) { this.github = github; this.signer = signer; this.clock = clock; }
    public synchronized String get(long installationId) {
        if (installationId < 1) throw new IllegalArgumentException("Invalid installation ID");
        var token = cache.get(installationId);
        if (token != null && token.expiresAt().isAfter(clock.instant().plusSeconds(60))) return token.value();
        var response = github.post("/app/installations/" + installationId + "/access_tokens", signer.sign(), Map.of());
        var value = response.path("token").asText();
        Instant expires;
        try { expires = Instant.parse(response.path("expires_at").asText()); }
        catch (RuntimeException malformed) { throw new GithubApiException(502, false, 0); }
        if (value.isBlank() || !expires.isAfter(clock.instant().plusSeconds(60))) throw new GithubApiException(502, false, 0);
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
        if (cache.size() >= 1000) cache.clear();
        cache.put(installationId, new Token(value, expires)); return value;
    }
    public synchronized void invalidate(long installationId) { cache.remove(installationId); }
}
