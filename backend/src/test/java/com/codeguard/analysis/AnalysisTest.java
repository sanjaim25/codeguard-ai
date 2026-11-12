package com.codeguard.analysis;

import com.codeguard.github.GithubPullRequests.*;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnalysisTest {
    private final DiffNormalizer diffs = new DiffNormalizer(1000);
    private DiffNormalizer.Normalized normalize(RawFile... files) { return diffs.normalize(new Snapshot("a".repeat(40), List.of(files), files.length, false)); }
    @Test void mapsOnlyAddedLinesAcrossHunksAndNoNewlineMarker() {
        var result = normalize(new RawFile("src/App.java", "modified", "@@ -3,2 +3,3 @@\n context\n-old\n+new\n+extra\n@@ -20 +21 @@\n-old\n+last\n\\ No newline at end of file\n", false));
        assertThat(result.files().getFirst().addedLines()).containsExactlyInAnyOrder(4, 5, 21);
        assertThat(result.incomplete()).isFalse();
    }
    @Test void filtersGeneratedSecretsLocksAndUnsafePaths() {
        String patch = "@@ -0,0 +1 @@\n+hello\n";
        var result = normalize(new RawFile(".env.json", "added", patch, false), new RawFile("node_modules/a.js", "added", patch, false), new RawFile("package-lock.json", "added", patch, false), new RawFile("../src/a.java", "added", patch, false), new RawFile("src/a.java", "added", patch, false), new RawFile("image.png", "added", null, true));
        assertThat(result.files()).extracting(ChangedFile::path).containsExactly("src/a.java");
        assertThat(result.excludedFiles()).isEqualTo(5);
        assertThat(result.incomplete()).isTrue();
    }
    @Test void malformedOrPartialHunksCannotCreateInlineLocations() {
        for (String patch : List.of("@@ -0,0 +1,2 @@\n+one\n", "@@ -0,0 +1 @@\n+one\n+extra\n", "@@ -0,0 +99999999999999999999 @@\n+x\n", "arbitrary text")) {
            var file = normalize(new RawFile("a.java", "added", patch, false)).files().getFirst();
            assertThat(file.truncated()).isTrue();
            assertThat(file.addedLines()).isEmpty();
        }
    }
    @Test void limitsContextAtLineBoundaryAndMarksIncomplete() {
        var result = new DiffNormalizer(256).normalize(new Snapshot("sha", List.of(new RawFile("a.java", "added", "@@ -0,0 +1,2 @@\n+short\n+" + "a".repeat(400), false)), 1, false));
        assertThat(result.files().getFirst().patch()).endsWith("\n").hasSizeLessThanOrEqualTo(256);
        assertThat(result.incomplete()).isTrue();
        assertThat(result.files().getFirst().addedLines()).isEmpty();
    }
    @Test void normalizesUnknownLocationsAndRejectsInvalidOutput() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var normalizer = new FindingNormalizer(factory.getValidator());
            var files = normalize(new RawFile("a.java", "added", "@@ -0,0 +1 @@\n+value\n", false)).files();
            var mapped = normalizer.normalize(List.of(finding("a.java", 1, .9), finding("a.java", 2, .9), finding("invented.java", 1, .9)), files);
            assertThat(mapped.get(0).line()).isEqualTo(1);
            assertThat(mapped.get(1).line()).isNull();
            assertThat(mapped.get(2).filePath()).isNull();
            assertThatThrownBy(() -> normalizer.normalize(List.of(finding("a.java", 1, Double.NaN)), files)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> normalizer.normalize(List.of(finding("a.java", -1, .9)), files)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void staticChecksInspectOnlyAddedExecutableLookingLines() {
        var files = normalize(new RawFile("a.java", "modified", "@@ -1,2 +1,3 @@\n-old.printStackTrace();\n context\n+error.printStackTrace();\n+// eval(input)\n", false)).files();
        var findings = new StaticChecks().analyze(files);
        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().line()).isEqualTo(2);
        assertThat(findings.getFirst().category()).isEqualTo(FindingData.Category.ERROR_HANDLING);
    }
    private FindingData finding(String path, int line, double confidence) {
        return new FindingData("Review this", "Description", path, line, FindingData.Severity.HIGH, FindingData.Category.BUG, "Fix it", confidence, "Explanation");
    }
    @Test void javaProcessExecutionIsNotDuplicatedAsStandaloneDynamicEvaluation() {
        var files = normalize(new RawFile("a.java", "added", "@@ -0,0 +1 @@\n+Runtime.getRuntime().exec(command);\n", false)).files();
        assertThat(new StaticChecks().analyze(files)).hasSize(1);
    }
}
