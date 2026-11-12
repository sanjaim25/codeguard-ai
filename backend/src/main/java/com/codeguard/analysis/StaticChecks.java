package com.codeguard.analysis;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static com.codeguard.analysis.FindingData.Category.*;
import static com.codeguard.analysis.FindingData.Severity.*;

/** Small, explicitly heuristic checks. No compilation, dependency installation, or code execution. */
@Component
public class StaticChecks {
    private record Rule(Pattern pattern, String title, String description, FindingData.Category category, FindingData.Severity severity, String fix) {}
    private static final List<Rule> RULES = List.of(
        new Rule(Pattern.compile("\\bRuntime\\.getRuntime\\(\\)\\.exec\\("), "Review process execution", "A newly added line invokes a system process. Check whether untrusted input can reach its arguments.", SECURITY, HIGH, "Use an allowlist of operations and separate fixed executable names from validated arguments."),
        new Rule(Pattern.compile("(?<![\\w.])(?:eval|exec)\\s*\\("), "Review dynamic code execution", "Dynamic execution may interpret untrusted input as code. This pattern requires human review.", INPUT_VALIDATION, HIGH, "Replace dynamic evaluation with a parser or a fixed set of supported operations."),
        new Rule(Pattern.compile("\\.printStackTrace\\s*\\("), "Avoid uncontrolled stack trace output", "A stack trace is written directly to standard error and may expose request data or bypass log controls.", ERROR_HANDLING, LOW, "Use structured logging with a safe message and appropriate redaction.")
    );
    public List<FindingData> analyze(List<ChangedFile> files) {
        List<FindingData> findings = new ArrayList<>();
        for (var file : files) {
            if (file.truncated()) continue;
            int lineNumber = 0;
            for (String line : file.patch().split("\n")) {
                if (line.startsWith("@@")) {
                    var matcher = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)").matcher(line);
                    if (matcher.find()) lineNumber = Integer.parseInt(matcher.group(1));
                } else if (line.startsWith("+")) {
                    String code = line.substring(1).stripLeading();
                    if (file.addedLines().contains(lineNumber) && !code.startsWith("//") && !code.startsWith("#") && !code.startsWith("*")) {
                        for (var rule : RULES) if (rule.pattern().matcher(code).find() && findings.size() < 50) {
                            findings.add(new FindingData(rule.title(), rule.description(), file.path(), lineNumber, rule.severity(), rule.category(), rule.fix(), 0.65, "Static heuristic matched a newly added line. Inspect its surrounding context; this is not proof of a defect."));
                        }
                    }
                    lineNumber++;
                } else if (line.startsWith(" ")) lineNumber++;
            }
        }
        return List.copyOf(findings);
    }
}
