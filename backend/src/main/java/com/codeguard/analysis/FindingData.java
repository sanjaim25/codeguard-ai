package com.codeguard.analysis;

import jakarta.validation.constraints.*;

/** Untrusted provider output must be validated and mapped before persistence. Null line means a general observation. */
public record FindingData(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 4000) String description,
    @Size(max = 500) String filePath,
    @Positive Integer line,
    @NotNull Severity severity,
    @NotNull Category category,
    @NotNull @Size(max = 4000) String suggestedFix,
    @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
    @NotBlank @Size(max = 4000) String explanation
) {
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
    public enum Category { BUG, SECURITY, NULL_HANDLING, ERROR_HANDLING, RESOURCE_LEAK, MAINTAINABILITY, PERFORMANCE, CONCURRENCY, INPUT_VALIDATION }
    public FindingData asGeneralObservation() {
        return new FindingData(title, description, filePath, null, severity, category, suggestedFix, confidence, explanation);
    }
}
