package com.codeguard.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class GithubClient {
    private final RestClient http;
    @Autowired
    public GithubClient(RestClient.Builder builder, @Value("${codeguard.github.api-url:https://api.github.com}") String baseUrl) {
        URI base = URI.create(baseUrl);
        if (!"https".equals(base.getScheme()) && !Set.of("localhost", "127.0.0.1").contains(base.getHost()))
            throw new IllegalArgumentException("GitHub API requires HTTPS");
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws java.io.IOException {
                super.prepareConnection(connection, method); connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(10)); factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = builder.baseUrl(baseUrl).requestFactory(factory)
            .defaultHeader("Accept", "application/vnd.github+json").defaultHeader("X-GitHub-Api-Version", "2026-03-10").build();
    }
    GithubClient(RestClient http) { this.http = http; }
    public JsonNode get(String path, String token) { return request(HttpMethod.GET, path, token, null); }
    public JsonNode post(String path, String token, Object body) { return request(HttpMethod.POST, path, token, body); }
    private JsonNode request(HttpMethod method, String path, String token, Object body) {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) throw new IllegalArgumentException("Relative GitHub API path required");
        try {
            var request = http.method(method).uri(path).headers(headers -> headers.setBearerAuth(token));
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body);
            return request.retrieve().onStatus(status -> status.is3xxRedirection(), (req, res) -> { throw new GithubApiException(res.getStatusCode().value(), false, 0); }).body(JsonNode.class);
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            var headers = error.getResponseHeaders();
            boolean limited = status == 429 || (status == 403 && headers != null &&
                ("0".equals(headers.getFirst("X-RateLimit-Remaining")) || headers.containsKey("Retry-After")));
            long delay = 0;
            if (headers != null) {
                try { delay = Long.parseLong(headers.getFirst("Retry-After")); } catch (Exception ignored) { /* Optional numeric header. */ }
                if (delay == 0 && limited) {
                    try { delay = Long.parseLong(headers.getFirst("X-RateLimit-Reset")) - Instant.now().getEpochSecond(); } catch (Exception ignored) { /* Optional timestamp. */ }
                }
            }
            throw new GithubApiException(status, limited || status >= 500, Math.max(0, Math.min(3600, delay)));
        } catch (RestClientException unavailable) { throw new GithubApiException(502, true, 0); }
    }
    public List<JsonNode> list(String path, String collection, String token) {
        List<JsonNode> result = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
            var response = get(path + "?per_page=100&page=" + page, token);
            var items = collection == null ? response : response.path(collection);
            if (!items.isArray()) throw new GithubApiException(502, false, 0);
            items.forEach(result::add);
            if (items.size() < 100) return result;
        }
        throw new GithubApiException(422, false, 0); // Fail closed rather than silently truncate authorization discovery.
    }
    public static String repositoryPath(String fullName) {
        if (fullName == null || !fullName.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || fullName.contains(".."))
            throw new IllegalArgumentException("Invalid GitHub repository name");
        return "/repos/" + fullName;
    }
}
