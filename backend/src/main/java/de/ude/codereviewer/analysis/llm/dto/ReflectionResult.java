package de.ude.codereviewer.analysis.llm.dto;

import java.util.List;

public record ReflectionResult(
    List<ReflectionEntry> reflections,
    List<String> missedIssues
) {
    public ReflectionResult {
        reflections = reflections == null ? List.of() : List.copyOf(reflections);
        missedIssues = missedIssues == null ? List.of() : List.copyOf(missedIssues);
    }
}