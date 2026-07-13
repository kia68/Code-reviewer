package de.ude.codereviewer.analysis.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    String provider,
    String apiKey,
    String model,
    String baseUrl,
    int maxTokensPerCall,
    int maxReflectRounds,
    boolean cacheEnabled,
    int cacheTtlHours,
    int dailyTokenBudget,
    RateLimit rateLimit
    
) {
    public record RateLimit(int callsPerPeriod, int periodSeconds) {}
}