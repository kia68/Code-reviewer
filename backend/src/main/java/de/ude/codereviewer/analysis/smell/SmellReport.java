package de.ude.codereviewer.analysis.smell;

import java.util.List;

public record SmellReport(List<DetectedSmell> smells) { }
