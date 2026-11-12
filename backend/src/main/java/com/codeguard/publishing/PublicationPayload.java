package com.codeguard.publishing;

import com.codeguard.analysis.*;
import java.util.*;

/** Bounded GitHub Markdown assembled from untrusted findings, never executable suggestions. */
public record PublicationPayload(String commit_id, String body, String event, List<Comment> comments) {
    public record Comment(String path, int line, String side, String body) {}
    public PublicationPayload { comments = List.copyOf(comments); }
    public static String marker(UUID id) { return "<!-- codeguard-publication:" + id + " -->"; }

    public static PublicationPayload create(UUID id, String sha, String summary, List<FindingData> findings,
                                             List<ChangedFile> freshFiles) {
        if (sha == null || !sha.matches("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})")) throw new IllegalArgumentException("Invalid commit SHA");
        if (findings.size() > 100) throw new IllegalArgumentException("Too many findings");
        Map<String, ChangedFile> files = new HashMap<>();
        freshFiles.forEach(file -> files.put(file.path(), file));
        List<Comment> comments = new ArrayList<>();
        StringBuilder general = new StringBuilder();
        int omitted = 0;
        for (var finding : findings) {
            var file = files.get(finding.filePath());
            if (comments.size() < 30 && file != null && !file.truncated() && finding.line() != null && file.addedLines().contains(finding.line())) {
                comments.add(new Comment(file.path(), finding.line(), "RIGHT", render(finding)));
            } else {
                String observation = "\n\n### " + finding.severity() + ": " + text(finding.title(), 200)
                    + "\n" + text(finding.description(), 800)
                    + "\n\nSuggested fix: " + text(finding.suggestedFix(), 500);
                if (general.length() + observation.length() <= 40_000) general.append(observation);
                else omitted++;
            }
        }
        String body = marker(id) + "\n## CodeGuard AI review\n\nAdvisory findings; verify before changing code."
            + "\n\n" + text(summary, 2000)
            + (general.isEmpty() ? "" : "\n\n## General observations\nLocations not validated for inline commenting are included here." + general)
            + (omitted == 0 ? "" : "\n\n" + omitted + " additional observations omitted due to the publishing size limit. See the dashboard.");
        return new PublicationPayload(sha, body, "COMMENT", comments);
    }
    private static String render(FindingData finding) {
        return "**" + finding.severity() + ": " + text(finding.title(), 200) + "**"
            + "\n\n" + text(finding.description(), 1200)
            + "\n\nSuggested fix: " + text(finding.suggestedFix(), 1200)
            + "\n\nExplanation: " + text(finding.explanation(), 1200);
    }
    // Encode Markdown punctuation before GitHub parses it. Break mentions to avoid notifications
    // originating in repository text or model output. Preserve readable newlines and Unicode.
    static String text(String value, int max) {
        if (value == null) return "";
        StringBuilder safe = new StringBuilder();
        value.codePoints().limit(max).forEach(c -> {
            if (c == '@') safe.append("@\u200b");
            else if ("&<>`*_{}[]()#+-.!|~\\".indexOf(c) >= 0) safe.append("&#").append(c).append(';');
            else if (c == '\n' || c >= 32 && c != 127) safe.appendCodePoint(c);
        });
        if (value.codePointCount(0, value.length()) > max) safe.append(" … [truncated]");
        return safe.toString();
    }
    @Override public String toString() { return "PublicationPayload[redacted]"; }
}
