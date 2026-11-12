package com.codeguard.ai;

import com.codeguard.analysis.*;
import java.math.BigDecimal;
import java.util.List;

public interface AIReviewProvider {
    record Input(List<ChangedFile> files, boolean privateRepository, boolean cloudOptIn) {
        public Input { files = List.copyOf(files); }
        @Override public String toString() { return "AIReviewInput[files=" + files.size() + "]"; }
    }
    record Usage(String model, long inputTokens, long outputTokens, boolean measuredTokens, BigDecimal estimatedCostUsd) {}
    record Output(String summary, List<FindingData> findings, Usage usage) {
        public Output { findings = List.copyOf(findings); }
        @Override public String toString() { return "AIReviewOutput[findings=" + findings.size() + "]"; }
    }
    String name();
    boolean external();
    /** Worst-case estimate to reserve durably before each attempt. */
    BigDecimal reservation(Input input);
    Output review(Input input);
}
