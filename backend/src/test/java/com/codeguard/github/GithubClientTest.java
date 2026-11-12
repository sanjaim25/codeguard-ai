package com.codeguard.github;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubClientTest {
    @Test void sendsBearerTokenAndReadsRepositoryPermissions() {
        var builder = RestClient.builder().baseUrl("https://api.github.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GithubClient(builder.build());
        server.expect(requestTo("https://api.github.com/user/installations/7/repositories?per_page=100&page=1"))
            .andExpect(header("Authorization", "Bearer test-user-token"))
            .andRespond(withSuccess("{\"repositories\":[{\"id\":1,\"permissions\":{\"push\":true}}]}", MediaType.APPLICATION_JSON));
        assertThat(client.list("/user/installations/7/repositories", "repositories", "test-user-token")).hasSize(1);
        server.verify();
    }
    @Test void classifiesRateLimitsWithoutLeakingBodies() {
        var builder = RestClient.builder().baseUrl("https://api.github.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GithubClient(builder.build());
        server.expect(requestTo("https://api.github.com/user")).andRespond(withStatus(HttpStatus.FORBIDDEN)
            .header("X-RateLimit-Remaining", "0").header("Retry-After", "30").body("private provider response"));
        assertThatThrownBy(() -> client.get("/user", "test-token")).isInstanceOfSatisfying(GithubApiException.class, error -> {
            assertThat(error.retryable()).isTrue(); assertThat(error.retryAfterSeconds()).isEqualTo(30);
            assertThat(error.getMessage()).doesNotContain("private provider");
        });
    }
    @Test void rejectsExternalPathsAndRedirects() {
        var builder = RestClient.builder().baseUrl("https://api.github.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GithubClient(builder.build());
        assertThatThrownBy(() -> client.get("//example.com", "secret")).isInstanceOf(IllegalArgumentException.class);
        server.expect(requestTo("https://api.github.com/user")).andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://example.com"));
        assertThatThrownBy(() -> client.get("/user", "secret")).isInstanceOf(GithubApiException.class);
    }
}
