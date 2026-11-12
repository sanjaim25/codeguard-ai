package com.codeguard.publishing;

import com.codeguard.analysis.*;
import com.codeguard.github.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubReviewPublisherTest {
    final GithubClient github = mock(GithubClient.class);
    final GithubPullRequests pulls = mock(GithubPullRequests.class);
    final InstallationTokens tokens = mock(InstallationTokens.class);
    final GithubReviewPublisher publisher = new GithubReviewPublisher(github, pulls, tokens, new DiffNormalizer(40000));
    final ObjectMapper json = new ObjectMapper();
    final String sha = "a".repeat(40);
    @Test void sendsOnePinnedCommentReviewAndDoesNotRetryAmbiguousErrors() throws Exception {
        when(tokens.get(7)).thenReturn("test-installation-token");
        when(pulls.metadata(7, "owner/repo", 1)).thenReturn(json.readTree("{\"state\":\"open\",\"head\":{\"sha\":\""+sha+"\"}}"));
        var payload = PublicationPayload.create(UUID.randomUUID(), sha, "Summary", List.of(), List.of());
        when(github.post("/repos/owner/repo/pulls/1/reviews", "test-installation-token", payload)).thenThrow(new GithubApiException(502, true, 0));
        assertThatThrownBy(() -> publisher.submitOnce(7, "owner/repo", 1, payload)).isInstanceOf(GithubApiException.class);
        verify(github, times(1)).post(anyString(), anyString(), any());
        verify(tokens, never()).invalidate(anyLong());
    }
    @Test void refusesStaleCommitBeforePosting() throws Exception {
        when(pulls.metadata(7, "owner/repo", 1)).thenReturn(json.readTree("{\"state\":\"closed\"}"));
        var payload = PublicationPayload.create(UUID.randomUUID(), sha, "Summary", List.of(), List.of());
        assertThatThrownBy(() -> publisher.submitOnce(7, "owner/repo", 1, payload)).isInstanceOf(StalePullRequestException.class);
        verifyNoInteractions(github, tokens);
    }
    @Test void acceptsConfirmedCommentedReview() throws Exception {
        when(tokens.get(7)).thenReturn("token");
        when(pulls.metadata(7, "owner/repo", 1)).thenReturn(json.readTree("{\"state\":\"open\",\"head\":{\"sha\":\""+sha+"\"}}"));
        when(github.post(anyString(), eq("token"), any())).thenReturn(json.readTree("{\"id\":321,\"commit_id\":\""+sha+"\",\"state\":\"COMMENTED\"}"));
        assertThat(publisher.submitOnce(7, "owner/repo", 1, PublicationPayload.create(UUID.randomUUID(), sha, "Summary", List.of(), List.of()))).isEqualTo(321);
    }
    @Test void reconciliationUsesMarkerCommitAndBotAndNeverPosts() {
        var id = UUID.randomUUID(); when(tokens.get(7)).thenReturn("token");
        var review = json.createObjectNode().put("id", 321).put("commit_id", sha).put("state", "COMMENTED")
            .put("body", PublicationPayload.marker(id)+"\nReview");
        review.putObject("user").put("type", "User");
        when(github.list(anyString(), isNull(), eq("token"))).thenReturn(List.of(review));
        assertThat(publisher.reconcile(id, 7, "owner/repo", 1, sha)).isEmpty();
        review.withObject("/user").put("type", "Bot");
        assertThat(publisher.reconcile(id, 7, "owner/repo", 1, sha)).hasValue(321);
        assertThat(publisher.reconcile(UUID.randomUUID(), 7, "owner/repo", 1, sha)).isEmpty();
        verify(github, never()).post(anyString(), anyString(), any());
    }
}
