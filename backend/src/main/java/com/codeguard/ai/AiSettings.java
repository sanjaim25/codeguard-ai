package com.codeguard.ai;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("codeguard.ai")
public record AiSettings(
    @Pattern(regexp = "mock|cloud") String provider,
    @NotBlank String baseUrl, String apiKey,
    @NotBlank @Size(max = 100) String model,
    @Min(256) @Max(200000) int maxDiffChars,
    @Min(256) @Max(32000) int maxOutputTokens,
    @Min(1024) @Max(250000) int maxInputTokens,
    @Min(1) @Max(16) int concurrency,
    @NotNull @DecimalMin("0.000001") BigDecimal maxCostUsd,
    @NotNull @DecimalMin("0.000001") BigDecimal inputPerMillion,
    @NotNull @DecimalMin("0.000001") BigDecimal outputPerMillion
) {
    @Override public String toString() { return "AiSettings[provider=" + provider + ", model=" + model + ", credentials=REDACTED]"; }
}
