package com.codeguard.analysis;

import jakarta.validation.Validator;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FindingNormalizer {
    private final Validator validator;
    public FindingNormalizer(Validator validator) { this.validator = validator; }
    public List<FindingData> normalize(List<FindingData> findings, List<ChangedFile> files) {
        if (findings == null || findings.size() > 100) throw new IllegalArgumentException("Invalid finding count");
        Map<String, ChangedFile> paths = new HashMap<>();
        files.forEach(file -> paths.put(file.path(), file));
        LinkedHashSet<FindingData> accepted = new LinkedHashSet<>();
        for (var finding : findings) {
            if (finding == null || !Double.isFinite(finding.confidence()) || !validator.validate(finding).isEmpty()) throw new IllegalArgumentException("Invalid finding structure");
            var file = paths.get(finding.filePath());
            if (file == null) {
                finding = new FindingData(finding.title(), finding.description(), null, null, finding.severity(), finding.category(), finding.suggestedFix(), finding.confidence(), finding.explanation());
            } else if (file.truncated() || finding.line() == null || !file.addedLines().contains(finding.line())) {
                finding = finding.asGeneralObservation();
            }
            accepted.add(finding);
        }
        return List.copyOf(accepted);
    }
}
