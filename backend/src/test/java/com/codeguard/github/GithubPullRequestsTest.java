package com.codeguard.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GithubPullRequestsTest {
    private final GithubClient client = mock(GithubClient.class);
    private final InstallationTokens tokens = mock(InstallationTokens.class);
    private final ObjectMapper json = new ObjectMapper();
    @Test void retrievesPatchesAndMarksMissingBinaryDiffAsIncomplete() throws Exception {
        when(tokens.get(7)).thenReturn("installation-token");
        var metadata = json.readTree("{\"state\":\"open\",\"head\":{\"sha\":\"abc\"},\"changed_files\":2}");
        when(client.get("/repos/team/repo/pulls/1", "installation-token")).thenReturn(metadata);
        when(client.get("/repos/team/repo/pulls/1/files?per_page=100&page=1", "installation-token")).thenReturn(json.readTree(
            "[{\"filename\":\"Main.java\",\"status\":\"modified\",\"patch\":\"@@ -1 +1 @@\\n-old\\n+new\"},{\"filename\":\"image.png\"}]"));
        var snapshot = new GithubPullRequests(client, tokens, 200).snapshot(7, "team/repo", 1, "abc");
        assertThat(snapshot.files()).hasSize(2); assertThat(snapshot.incomplete()).isTrue();
        assertThat(snapshot.files().get(1).patch()).isNull();
        verify(client, times(2)).get("/repos/team/repo/pulls/1", "installation-token");
    }
    @Test void rejectsHeadChangedWhileFilesWereFetched() throws Exception {
        when(tokens.get(7)).thenReturn("token");
        when(client.get("/repos/team/repo/pulls/1", "token")).thenReturn(
            json.readTree("{\"state\":\"open\",\"head\":{\"sha\":\"old\"},\"changed_files\":0}"),
            json.readTree("{\"state\":\"open\",\"head\":{\"sha\":\"new\"},\"changed_files\":0}"));
        assertThatThrownBy(() -> new GithubPullRequests(client, tokens, 200).snapshot(7, "team/repo", 1, "old"))
            .isInstanceOf(StalePullRequestException.class);
    }
    @Test void invalidatesRejectedInstallationTokenAndRetriesReadOnce() throws Exception {
        when(tokens.get(7)).thenReturn("old", "new");
        when(client.get(anyString(), eq("old"))).thenThrow(new GithubApiException(401, false, 0));
        when(client.get(anyString(), eq("new"))).thenReturn(json.readTree("{}"));
        new GithubPullRequests(client, tokens, 200).metadata(7, "team/repo", 1);
        verify(tokens).invalidate(7); verify(client, times(2)).get(anyString(), anyString());
    }
}
