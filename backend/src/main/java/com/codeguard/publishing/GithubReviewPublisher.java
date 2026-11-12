package com.codeguard.publishing;

import com.codeguard.analysis.*;
import com.codeguard.github.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Network boundary only. The caller must durably enter SENDING before invoking submitOnce. */
@Service
public class GithubReviewPublisher {
    private final GithubClient github;
    private final GithubPullRequests pulls;
    private final InstallationTokens tokens;
    private final DiffNormalizer normalizer;
    public GithubReviewPublisher(GithubClient github, GithubPullRequests pulls, InstallationTokens tokens, DiffNormalizer normalizer) {
        this.github = github; this.pulls = pulls; this.tokens = tokens; this.normalizer = normalizer;
    }
    public PublicationPayload prepare(UUID publicationId, long installationId, String repository, int number,
                                      String sha, String summary, List<FindingData> findings) {
        var snapshot = pulls.snapshot(installationId, repository, number, sha);
        return PublicationPayload.create(publicationId, sha, summary, findings, normalizer.normalize(snapshot).files());
    }
    public long submitOnce(long installationId, String repository, int number, PublicationPayload payload) {
        // No retry, including 401. Any exception after entering SENDING must be reconciled.
        GithubPullRequests.requireCurrent(pulls.metadata(installationId, repository, number), payload.commit_id());
        var response = github.post(path(repository, number), tokens.get(installationId), payload);
        if (response == null || !response.path("id").canConvertToLong() || response.path("id").asLong() <= 0
            || !payload.commit_id().equals(response.path("commit_id").asText())
            || !"COMMENTED".equals(response.path("state").asText())) throw new GithubApiException(502, false, 0);
        return response.path("id").asLong();
    }
    public OptionalLong reconcile(UUID publicationId, long installationId, String repository, int number, String sha) {
        // Absence is NOT permission to resend: GitHub may have committed a request whose response was lost.
        var reviews = github.list(path(repository, number), null, tokens.get(installationId));
        return reviews.stream().filter(review -> sha.equals(review.path("commit_id").asText())
                && "COMMENTED".equals(review.path("state").asText())
                && review.path("body").asText().startsWith(PublicationPayload.marker(publicationId) + "\n")
                && "Bot".equals(review.path("user").path("type").asText())
                && review.path("id").canConvertToLong() && review.path("id").asLong() > 0)
            .mapToLong(review -> review.path("id").asLong()).findFirst();
    }
    private static String path(String repository, int number) {
        if (number < 1) throw new IllegalArgumentException("Invalid pull request number");
        return GithubClient.repositoryPath(repository) + "/pulls/" + number + "/reviews";
    }
}
