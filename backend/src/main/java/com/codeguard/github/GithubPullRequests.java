package com.codeguard.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GithubPullRequests {
    public record RawFile(String path, String status, String patch, boolean truncated) {}
    public record Snapshot(String headSha, List<RawFile> files, int totalFiles, boolean incomplete) {}
    private final GithubClient github;
    private final InstallationTokens tokens;
    private final int maxFiles;
    public GithubPullRequests(GithubClient github, InstallationTokens tokens, @Value("${codeguard.github.max-files:200}") int maxFiles) {
        if (maxFiles < 1 || maxFiles > 3000) throw new IllegalArgumentException("GitHub file limit must be 1–3000");
        this.github = github; this.tokens = tokens; this.maxFiles = maxFiles;
    }
    public JsonNode metadata(long installationId, String repository, int number) {
        if (number < 1) throw new IllegalArgumentException("Invalid pull request number");
        return authorized(installationId, token -> github.get(GithubClient.repositoryPath(repository) + "/pulls/" + number, token));
    }
    public Snapshot snapshot(long installationId, String repository, int number, String expectedSha) {
        var before = metadata(installationId, repository, number); requireCurrent(before, expectedSha);
        int total = before.path("changed_files").asInt(-1);
        if (total < 0) throw new GithubApiException(502, false, 0);
        List<RawFile> files = new ArrayList<>();
        boolean incomplete = total > maxFiles;
        int remainingChars = 200_000;
        Instant deadline = Instant.now().plusSeconds(120);
        for (int page = 1; page <= 30 && files.size() < Math.min(total, maxFiles); page++) {
            if (Instant.now().isAfter(deadline)) throw new GithubApiException(504, true, 0);
            String path = GithubClient.repositoryPath(repository) + "/pulls/" + number + "/files?per_page=100&page=" + page;
            var response = authorized(installationId, token -> github.get(path, token));
            if (response == null || !response.isArray()) throw new GithubApiException(502, false, 0);
            if (response.isEmpty()) { incomplete = files.size() < total; break; }
            for (var remote : response) {
                if (files.size() >= maxFiles) break;
                String file = remote.path("filename").asText("");
                if (file.isBlank() || file.length() > 500) { incomplete = true; continue; }
                String patch = remote.path("patch").isTextual() ? remote.path("patch").asText() : null;
                boolean truncated = patch == null;
                if (patch != null) {
                    int allowed = Math.min(20_000, remainingChars);
                    if (patch.length() > allowed) { patch = patch.substring(0, allowed); truncated = true; }
                    remainingChars -= patch.length();
                }
                files.add(new RawFile(file, remote.path("status").asText("modified"), patch, truncated));
                incomplete |= truncated;
            }
            if (remainingChars == 0 || response.size() < 100) break;
        }
        incomplete |= files.size() < total;
        requireCurrent(metadata(installationId, repository, number), expectedSha);
        return new Snapshot(expectedSha, List.copyOf(files), total, incomplete);
    }
    public static void requireCurrent(JsonNode metadata, String expectedSha) {
        if (!"open".equals(metadata.path("state").asText()) || !expectedSha.equals(metadata.path("head").path("sha").asText()))
            throw new StalePullRequestException();
    }
    private <T> T authorized(long installationId, Function<String, T> operation) {
        try { return operation.apply(tokens.get(installationId)); }
        catch (GithubApiException failure) {
            if (failure.status() != 401) throw failure;
            tokens.invalidate(installationId); return operation.apply(tokens.get(installationId));
        }
    }
}
