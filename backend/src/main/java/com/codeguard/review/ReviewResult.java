package com.codeguard.review;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "review_results")
public class ReviewResult extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(unique = true) private ReviewJob job;
    @Column(nullable = false, length = 40) private String provider;
    @Column(nullable = false, length = 8000) private String summary;
    @Column(nullable = false, columnDefinition = "text") private String diffJson;
    @Column(nullable = false) private int excludedFiles;
    @Column(nullable = false) private boolean incomplete;
    protected ReviewResult() {}
    public ReviewResult(ReviewJob job, String provider, String summary, String diffJson, int excludedFiles, boolean incomplete) {
        this.job = job; this.provider = provider; this.summary = summary; this.diffJson = diffJson;
        this.excludedFiles = excludedFiles; this.incomplete = incomplete;
    }
    public ReviewJob getJob() { return job; }
    public String getProvider() { return provider; }
    public String getSummary() { return summary; }
    public String getDiffJson() { return diffJson; }
    public int getExcludedFiles() { return excludedFiles; }
    public boolean isIncomplete() { return incomplete; }
}
