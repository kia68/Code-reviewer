package de.ude.codereviewer.analysis.smell;

import de.ude.codereviewer.review.model.Severity;

public record DetectedSmell(
        String filePath, int lineNumber, String category, Severity severity, String description, String suggestion) { }
