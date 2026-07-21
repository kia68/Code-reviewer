package de.ude.codereviewer.analysis.llm.dto;

public record ReflectionEntry(
    int findingIndex,
    String verdict,
    String revisedSeverity,
    String reasoning 
) {}

