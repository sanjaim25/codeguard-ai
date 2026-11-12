package com.codeguard.analysis;

import com.codeguard.github.GithubPullRequests.Snapshot;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parses data only; repository paths are never opened and repository code is never executed. */
@Component
public class DiffNormalizer {
    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");
    private static final Set<String> EXTENSIONS = Set.of("java", "kt", "kts", "js", "jsx", "ts", "tsx", "py", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "swift", "scala", "sql", "sh", "yml", "yaml", "json", "xml", "html", "css", "scss", "vue", "svelte", "tf");
    private static final Set<String> EXCLUDED = Set.of("node_modules", "vendor", "dist", "build", "target", ".git", "coverage", "generated", ".next", "secrets");
    private final int maxChars;
    public record Normalized(List<ChangedFile> files, int excludedFiles, boolean incomplete) {
        public Normalized { files = List.copyOf(files); }
    }
    public DiffNormalizer(@Value("${codeguard.ai.max-diff-chars:40000}") int maxChars) {
        if (maxChars < 256 || maxChars > 200_000) throw new IllegalArgumentException("Diff limit must be 256–200000 characters");
        this.maxChars = maxChars;
    }
    public Normalized normalize(Snapshot snapshot) {
        List<ChangedFile> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int remaining = maxChars, excluded = Math.max(0, snapshot.totalFiles() - snapshot.files().size());
        boolean incomplete = snapshot.incomplete();
        for (var file : snapshot.files()) {
            if (!eligible(file.path()) || !seen.add(file.path()) || file.patch() == null || file.patch().isBlank() || remaining == 0) {
                excluded++; incomplete = true; continue;
            }
            String patch = file.patch().replace("\r\n", "\n");
            boolean truncated = file.truncated();
            if (patch.length() > remaining) {
                int end = patch.lastIndexOf('\n', remaining - 1);
                patch = end < 0 ? "" : patch.substring(0, end + 1);
                truncated = true;
            }
            if (patch.isBlank()) { excluded++; incomplete = true; continue; }
            var parsed = addedLines(patch);
            truncated |= !parsed.valid();
            files.add(new ChangedFile(file.path(), file.status(), patch, parsed.lines(), truncated));
            remaining -= patch.length(); incomplete |= truncated;
        }
        return new Normalized(files, excluded, incomplete);
    }
    public static boolean eligible(String path) {
        if (path == null || path.isBlank() || path.length() > 500 || path.startsWith("/") || path.indexOf('\\') >= 0 || path.chars().anyMatch(c -> c < 32 || c == 127)) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        for (String part : lower.split("/", -1)) if (part.isEmpty() || part.equals("..") || part.equals(".") || EXCLUDED.contains(part)) return false;
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        if (name.startsWith(".env") || name.contains(".min.") || name.endsWith(".lock") || Set.of("package-lock.json", "pnpm-lock.yaml", "yarn.lock", "composer.lock", "credentials.json").contains(name)) return false;
        return name.equals("dockerfile") || EXTENSIONS.contains(name.substring(name.lastIndexOf('.') + 1));
    }
    private record Parsed(Set<Integer> lines, boolean valid) {}
    private static Parsed addedLines(String patch) {
        Set<Integer> added = new LinkedHashSet<>();
        long next = 0, oldRemaining = 0, newRemaining = 0;
        boolean inHunk = false;
        try {
            String[] lines = patch.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i == lines.length - 1 && line.isEmpty()) break;
                var hunk = HUNK.matcher(line);
                if (hunk.matches()) {
                    if (inHunk && (oldRemaining != 0 || newRemaining != 0)) return new Parsed(Set.of(), false);
                    long oldStart = Long.parseLong(hunk.group(1));
                    oldRemaining = hunk.group(2) == null ? 1 : Long.parseLong(hunk.group(2));
                    next = Long.parseLong(hunk.group(3));
                    newRemaining = hunk.group(4) == null ? 1 : Long.parseLong(hunk.group(4));
                    if (next + newRemaining > Integer.MAX_VALUE || oldStart + oldRemaining > Integer.MAX_VALUE || (next == 0 && newRemaining > 0) || (oldStart == 0 && oldRemaining > 0)) return new Parsed(Set.of(), false);
                    inHunk = true; continue;
                }
                if (line.equals("\\ No newline at end of file")) continue;
                if (!inHunk || line.isEmpty()) return new Parsed(Set.of(), false);
                switch (line.charAt(0)) {
                    case '+' -> { if (--newRemaining < 0) return new Parsed(Set.of(), false); added.add((int) next++); }
                    case '-' -> { if (--oldRemaining < 0) return new Parsed(Set.of(), false); }
                    case ' ' -> { if (--oldRemaining < 0 || --newRemaining < 0) return new Parsed(Set.of(), false); next++; }
                    default -> { return new Parsed(Set.of(), false); }
                }
            }
            // A cut-off hunk cannot be used for inline publishing until the full diff is revalidated.
            return inHunk && oldRemaining == 0 && newRemaining == 0 ? new Parsed(added, true) : new Parsed(Set.of(), false);
        } catch (NumberFormatException invalid) { return new Parsed(Set.of(), false); }
    }
}
