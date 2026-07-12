package de.ude.codereviewer.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(int longMethodLineThreshold, int maxNestingDepth) { }
