package com.codeguard.analysis;

import com.codeguard.common.BaseEntity;
import com.codeguard.review.ReviewResult;
import jakarta.persistence.*;

@Entity
@Table(name = "review_findings")
public class ReviewFinding extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ReviewResult result;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 4000) private String description;
    @Column(length = 500) private String filePath;
    private Integer line;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FindingData.Severity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private FindingData.Category category;
    @Column(nullable = false, length = 4000) private String suggestedFix;
    @Column(nullable = false) private double confidence;
    @Column(nullable = false, length = 4000) private String explanation;
    protected ReviewFinding() {}
    public ReviewFinding(ReviewResult result, FindingData finding) {
        this.result = result; title = finding.title(); description = finding.description(); filePath = finding.filePath();
        line = finding.line(); severity = finding.severity(); category = finding.category(); suggestedFix = finding.suggestedFix();
        confidence = finding.confidence(); explanation = finding.explanation();
    }
    public FindingData data() { return new FindingData(title, description, filePath, line, severity, category, suggestedFix, confidence, explanation); }
}
