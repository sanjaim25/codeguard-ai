package com.codeguard.ai;

import com.codeguard.analysis.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AIReviewProviderTest {
    private final ObjectMapper json = new ObjectMapper();
    private ValidatorFactory validation;
    private StructuredReviewParser parser;
    private MockRestServiceServer server;
    private CloudAIReviewProvider cloud;
    private RestClient http;
    private final List<ChangedFile> files = List.of(new ChangedFile("src/A.java", "added", "@@ -0,0 +1 @@\n+eval(input);\n", Set.of(1), false));
    @BeforeEach void setup() {
        validation = Validation.buildDefaultValidatorFactory();
        parser = new StructuredReviewParser(json, new FindingNormalizer(validation.getValidator()));
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        http = builder.build();
        cloud = new CloudAIReviewProvider(http, json, parser, settings("0.05"));
    }
    @AfterEach void close() { validation.close(); server.verify(); }
    private AiSettings settings(String maxCost) { return new AiSettings("cloud", "https://api.openai.com/v1", "test-secret", "test-model", 40000, 4000, 64000, 1, new BigDecimal(maxCost), new BigDecimal("0.4"), new BigDecimal("1.6")); }
    private AIReviewProvider.Input input(boolean privateRepo, boolean optIn) { return new AIReviewProvider.Input(files, privateRepo, optIn); }
    private String response(String text) throws Exception {
        return json.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text)))), "usage", Map.of("input_tokens", 100, "output_tokens", 50)));
    }
    @Test void sendsStrictSchemaWithoutToolsAndMeasuresUsage() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-secret"))
            .andExpect(jsonPath("$.store").value(false)).andExpect(jsonPath("$.text.format.strict").value(true))
            .andExpect(jsonPath("$.text.format.type").value("json_schema")).andExpect(jsonPath("$.tools").doesNotExist())
            .andExpect(jsonPath("$.max_output_tokens").value(4000))
            .andRespond(withSuccess(response("{\"summary\":\"Check dynamic evaluation.\",\"findings\":[]}"), MediaType.APPLICATION_JSON));
        var result = cloud.review(input(true, true));
        assertThat(result.usage().measuredTokens()).isTrue();
        assertThat(result.usage().estimatedCostUsd()).isEqualByComparingTo("0.00012");
        assertThat(cloud.reservation(input(true, true))).isGreaterThan(result.usage().estimatedCostUsd());
    }
    @Test void refusesPrivateCodeWithoutOptInAndOverBudgetBeforeSending() {
        assertThatThrownBy(() -> cloud.review(input(true, false))).hasMessage("CLOUD_CONSENT_REQUIRED");
        var expensive = new CloudAIReviewProvider(http, json, parser, settings("0.00001"));
        assertThatThrownBy(() -> expensive.review(input(false, false))).hasMessage("AI_COST_BUDGET");
    }
    @Test void rateLimitIsRetryableAndNeverIncludesRemoteBody() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "45").body("private code and credentials"));
        assertThatThrownBy(() -> cloud.review(input(false, false))).isInstanceOfSatisfying(AiProviderException.class, error -> {
            assertThat(error.retryable()).isTrue(); assertThat(error.retryAfterSeconds()).isEqualTo(45); assertThat(error.getMessage()).isEqualTo("AI_RATE_LIMIT");
        });
    }
    @Test void redirectsAreNotRetried() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT).header("Location", "https://evil.example"));
        assertThatThrownBy(() -> cloud.review(input(false, false))).isInstanceOfSatisfying(AiProviderException.class, error -> assertThat(error.retryable()).isFalse());
    }
    @Test void refusesProviderRefusalAndLabelsMissingUsageAsEstimated() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}", MediaType.APPLICATION_JSON));
        var noUsage = json.readTree(response("{\"summary\":\"Review\",\"findings\":[]}"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) noUsage).remove("usage");
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(json.writeValueAsString(noUsage), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> cloud.review(input(false, false))).hasMessage("AI_REFUSAL");
        var output = cloud.review(input(false, false));
        assertThat(output.usage().measuredTokens()).isFalse();
        assertThat(output.usage().estimatedCostUsd()).isEqualByComparingTo(cloud.reservation(input(false, false)));
    }
    @Test void rejectsIncompleteAndMalformedProviderResponses() throws Exception {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("{\"status\":\"incomplete\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(response("{\"summary\":\"Fine\",\"findings\":[],\"command\":\"run me\"}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> cloud.review(input(false, false))).hasMessage("AI_INCOMPLETE_RESPONSE");
        assertThatThrownBy(() -> cloud.review(input(false, false))).hasMessage("AI_INVALID_RESPONSE");
        assertThatThrownBy(() -> cloud.review(input(false, false))).hasMessage("AI_INVALID_RESPONSE");
    }
    @Test void strictParserRejectsCoercionsMissingKeysAndDuplicateKeys() throws Exception {
        var finding = new FindingData("Potential problem", "Description", "src/A.java", 1, FindingData.Severity.HIGH, FindingData.Category.BUG, "Fix", .8, "Explanation");
        String good = json.writeValueAsString(Map.of("summary", "Review", "findings", List.of(finding)));
        assertThat(parser.parse(good, files).findings().getFirst().line()).isEqualTo(1);
        for (String bad : List.of(good.replace("\"confidence\":0.8", "\"confidence\":\"0.8\""), good.replace("\"line\":1", "\"line\":1.2"), good.replace("\"category\":\"BUG\",", ""), "{\"summary\":\"a\",\"summary\":\"b\",\"findings\":[]}", good + " {}")) {
            assertThatThrownBy(() -> parser.parse(bad, files)).hasMessage("AI_INVALID_RESPONSE");
        }
    }
    @Test void mockIsLocalFreeAndExplicitlyLabeled() {
        var mock = new MockAIReviewProvider();
        var result = mock.review(input(true, false));
        assertThat(mock.external()).isFalse();
        assertThat(result.summary()).contains("No cloud AI analysis");
        assertThat(result.usage().estimatedCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.usage().measuredTokens()).isFalse();
        assertThat(settings(".05").toString()).doesNotContain("test-secret");
        assertThat(input(true, true).toString()).doesNotContain("eval");
    }
}
