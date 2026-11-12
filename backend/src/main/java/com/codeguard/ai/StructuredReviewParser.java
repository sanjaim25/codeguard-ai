package com.codeguard.ai;

import com.codeguard.analysis.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Strict local validation is required even when a provider advertises schema adherence. */
@Component
public class StructuredReviewParser {
    private static final Set<String> FIELDS = Set.of("title", "description", "filePath", "line", "severity", "category", "suggestedFix", "confidence", "explanation");
    private final ObjectMapper json;
    private final FindingNormalizer normalizer;
    private final JsonNode schema;
    public record Parsed(String summary, List<FindingData> findings) {}
    public StructuredReviewParser(ObjectMapper json, FindingNormalizer normalizer) {
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.normalizer = normalizer;
        try (var stream = getClass().getResourceAsStream("/ai/review-schema.json")) {
            if (stream == null) throw new IllegalStateException("Review schema is missing");
            this.schema = this.json.readTree(stream);
        } catch (IOException invalid) { throw new IllegalStateException("Review schema cannot be loaded"); }
    }
    public JsonNode schema() { return schema.deepCopy(); }
    public Parsed parse(String text, List<ChangedFile> files) {
        try {
            if (text == null || text.length() > 250_000) throw invalid();
            var root = json.readTree(text);
            requireFields(root, Set.of("summary", "findings"));
            var summary = root.get("summary");
            if (!summary.isTextual() || summary.asText().isBlank() || summary.asText().length() > 4000) throw invalid();
            var items = root.get("findings");
            if (!items.isArray() || items.size() > 50) throw invalid();
            List<FindingData> findings = new ArrayList<>();
            for (var item : items) {
                requireFields(item, FIELDS);
                for (String field : List.of("title", "description", "severity", "category", "suggestedFix", "explanation")) if (!item.get(field).isTextual()) throw invalid();
                if (!item.get("filePath").isNull() && !item.get("filePath").isTextual()) throw invalid();
                var line = item.get("line");
                if (!line.isNull() && (!line.isIntegralNumber() || !line.canConvertToInt())) throw invalid();
                if (!item.get("confidence").isNumber()) throw invalid();
                findings.add(json.treeToValue(item, FindingData.class));
            }
            return new Parsed(summary.asText(), normalizer.normalize(findings, files));
        } catch (IOException | IllegalArgumentException failure) { throw invalid(); }
    }
    private void requireFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || node.size() != fields.size()) throw invalid();
        for (String field : fields) if (!node.has(field)) throw invalid();
    }
    private static AiProviderException invalid() { return new AiProviderException("AI_INVALID_RESPONSE", false); }
}
