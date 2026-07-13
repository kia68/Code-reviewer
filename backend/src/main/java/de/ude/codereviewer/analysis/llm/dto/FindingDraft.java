package de.ude.codereviewer.analysis.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FindingDraft(
    String type,
    String severity,

    @JsonProperty("lineStart")
    int lineStart,
    @JsonProperty("lineEnd")
    int lineEnd,

    String message,
    String suggestion

    double confidence
) {}