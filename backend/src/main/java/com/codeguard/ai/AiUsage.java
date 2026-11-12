package com.codeguard.ai;

import com.codeguard.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ai_usage_records", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "attempt"}))
public class AiUsage extends BaseEntity {
    @Column(nullable = false) private UUID jobId;
    @Column(nullable = false) private int attempt;
    @Column(nullable = false, length = 40) private String provider;
    @Column(nullable = false, length = 100) private String model;
    @Column(nullable = false) private long inputTokens;
    @Column(nullable = false) private long outputTokens;
    @Column(nullable = false) private boolean measuredTokens;
    @Column(precision = 14, scale = 8) private BigDecimal estimatedCostUsd;
    protected AiUsage() {}
    public AiUsage(UUID jobId, int attempt, String provider, String model, long inputTokens, long outputTokens, boolean measuredTokens, BigDecimal estimatedCostUsd) {
        this.jobId = jobId; this.attempt = attempt; this.provider = provider; this.model = model;
        this.inputTokens = inputTokens; this.outputTokens = outputTokens; this.measuredTokens = measuredTokens; this.estimatedCostUsd = estimatedCostUsd;
    }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public boolean isMeasuredTokens() { return measuredTokens; }
    public BigDecimal getEstimatedCostUsd() { return estimatedCostUsd; }
}
