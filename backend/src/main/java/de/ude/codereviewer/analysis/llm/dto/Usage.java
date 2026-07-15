package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token-Verbrauch eines API-Calls – wichtig für das TokenBudgetGuard-Tracking.
 */
public record Usage(
    @JsonProperty("input_tokens")
    int inputTokens,

    @JsonProperty("output_tokens")
    int outputTokens
) {
    public int total() {
        return inputTokens + outputTokens;
    }
}