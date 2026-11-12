package com.codeguard.publishing;

import com.codeguard.analysis.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PublicationTest {
    static final String SHA = "a".repeat(40);
    static FindingData finding(String path, Integer line) {
        return new FindingData("Untrusted @owner <script>", "[click](https://evil.test) **text**", path, line,
            FindingData.Severity.HIGH, FindingData.Category.SECURITY, "```suggestion\nunsafe\n```", .8, "Needs review");
    }
    @Test void onlyFreshCompleteAddedLinesBecomeInlineComments() {
        var fresh = List.of(new ChangedFile("A.java", "modified", "", Set.of(4), false),
            new ChangedFile("B.java", "modified", "", Set.of(4), true));
        var id = UUID.randomUUID();
        var payload = PublicationPayload.create(id, SHA, "Summary", List.of(finding("A.java", 4), finding("A.java", 3),
            finding("B.java", 4), finding("missing.java", 4), finding(null, null)), fresh);
        assertThat(payload.comments()).hasSize(1);
        assertThat(payload.comments().getFirst().line()).isEqualTo(4);
        assertThat(payload.comments().getFirst().side()).isEqualTo("RIGHT");
        assertThat(payload.body()).startsWith(PublicationPayload.marker(id)).contains("General observations");
        assertThat(payload.event()).isEqualTo("COMMENT");
        assertThat(payload.commit_id()).isEqualTo(SHA);
    }
    @Test void escapesMarkdownHtmlMentionsAndSuggestionFences() {
        var payload = PublicationPayload.create(UUID.randomUUID(), SHA, "@team <script>", List.of(finding("A.java", 4)),
            List.of(new ChangedFile("A.java", "modified", "", Set.of(4), false)));
        assertThat(payload.body()).doesNotContain("@team", "<script>").contains("@\u200bteam", "&#60;script&#62;");
        assertThat(payload.comments().getFirst().body()).doesNotContain("[click]", "```", "@owner", "<script>");
        assertThat(payload.toString()).isEqualTo("PublicationPayload[redacted]");
    }
    @Test void boundsCommentsAndGeneralBodyEvenWithEscapingExpansion() {
        var finding = new FindingData("<".repeat(200), "<".repeat(4000), "A.java", 4,
            FindingData.Severity.HIGH, FindingData.Category.SECURITY, "<".repeat(4000), .5, "<".repeat(4000));
        var payload = PublicationPayload.create(UUID.randomUUID(), SHA, "<".repeat(8000), Collections.nCopies(100, finding),
            List.of(new ChangedFile("A.java", "modified", "", Set.of(4), false)));
        assertThat(payload.comments()).hasSize(30);
        assertThat(payload.body().length()).isLessThan(60_000);
        assertThat(payload.body()).contains("omitted due to the publishing size limit");
        assertThatThrownBy(() -> PublicationPayload.create(UUID.randomUUID(), "bad", "", List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ambiguousSubmissionCannotBeRestartedAndCanBeReconciled() {
        var publication = new Publication(UUID.randomUUID(), UUID.randomUUID(), false);
        Instant now = Instant.parse("2026-10-16T00:00:00Z");
        assertThat(publication.begin(now)).isTrue();
        assertThat(publication.begin(now)).isFalse();
        assertThat(publication.abandoned(now.plusSeconds(301))).isTrue();
        publication.unknown();
        assertThat(publication.begin(now)).isFalse();
        publication.fail("RETRY"); publication.cancel();
        assertThat(publication.getStatus()).isEqualTo(Publication.Status.UNKNOWN);
        publication.published(123);
        assertThat(publication.getStatus()).isEqualTo(Publication.Status.PUBLISHED);
        assertThat(publication.getGithubReviewId()).isEqualTo(123);
        publication.unknown();
        assertThat(publication.begin(now)).isFalse();
    }
}
