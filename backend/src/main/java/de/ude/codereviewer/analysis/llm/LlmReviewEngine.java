package de.ude.codereviewer.analysis.llm;

import de.ude.codereviewer.analysis.ReviewEngine;
import de.ude.codereviewer.analysis.llm.cache.LlmReviewCache;
import de.ude.codereviewer.analysis.llm.dto.FindingDraft;
import de.ude.codereviewer.analysis.llm.dto.ReflectionResult;
import de.ude.codereviewer.analysis.llm.exception.LlmBudgetExceededException;
import de.ude.codereviewer.analysis.llm.ratelimit.TokenBudgetGuard;
import de.ude.codereviewer.analysis.smell.DetectedSmell;
import de.ude.codereviewer.review.model.Severity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmReviewEngine implements ReviewEngine {

    private final ClaudeClient claudeClient;
    private final OpenAiClient openAiClient;
    private final ClaudeToolSchemas toolSchemas;
    private final LlmReviewCache cache;
    private final TokenBudgetGuard budgetGuard;
    private final LlmProperties properties;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Initialized LlmReviewEngine for provider={}, model={}, baseUrl={}",
            properties.provider(), properties.model(), properties.baseUrl());
    }

    private static final String PROMPT_VERSION = "v1";
    private volatile boolean budgetExceeded = false;

    @Override
    public List<DetectedSmell> analyze(Path sourcePath) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("LLM API key not configured — skipping LLM review");
            return List.of();
        }
        budgetExceeded = false;
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(javaFile -> {
                    try {
                        return analyzeFile(sourcePath, javaFile).stream();
                    } catch (LlmBudgetExceededException e) {
                        log.warn("Token budget exceeded, skipping remaining files: {}", e.getMessage());
                        budgetExceeded = true;
                        return Stream.empty();
                    } catch (Exception e) {
                        log.warn("LLM analysis failed for {}: {}", javaFile, e.getMessage());
                        return Stream.empty();
                    }
                })
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte Quellverzeichnis nicht durchsuchen: " + sourcePath, e);
        }
    }

    @Override
    public FindingSource source() {
        return FindingSource.LLM;
    }

    private List<DetectedSmell> analyzeFile(Path sourcePath, Path javaFile) {
        if (budgetExceeded) return List.of();

        String relativePath = sourcePath.relativize(javaFile).toString();
        String content;
        try {
            content = Files.readString(javaFile);
        } catch (IOException e) {
            log.warn("Konnte Datei nicht lesen, überspringe LLM-Review: {}", relativePath, e);
            return List.of();
        }

        if (content.isBlank() || content.lines().count() < 3) {
            return List.of();
        }

        int lineCount = (int) content.lines().count();
        String cacheKey = cache.buildKey(content, PROMPT_VERSION, properties.model());

        List<FindingDraft> refinedFindings = cache.get(cacheKey, () -> runCycle(content));

        return refinedFindings.stream()
            .map(draft -> toDetectedSmell(relativePath, draft, lineCount))
            .toList();
    }

    private List<FindingDraft> runCycle(String code) {
        if ("openai".equalsIgnoreCase(properties.provider())) {
            log.info("OpenAI-Call: model={} baseUrl={}", properties.model(), properties.baseUrl());
            int estimate = code.length() / 4 + 500;
            budgetGuard.checkBudget(estimate);
            return openAiClient.requestFindings(PromptTemplates.generatePrompt(code));
        }

        int rounds = Math.max(0, properties.maxReflectRounds());
        budgetGuard.checkBudget(estimateTokens(code, rounds));

        List<FindingDraft> findings = generate(code);

        for (int i = 0; i < rounds; i++) {
            ReflectionResult reflection = reflect(code, findings);
            findings = refine(code, findings, reflection);
        }

        return findings;
    }

    private List<FindingDraft> generate(String code) {
        var resp = claudeClient.callWithTool(
            PromptTemplates.generatePrompt(code),
            toolSchemas.submitFindings(),
            "submit_findings"
        );
        return resp.extractToolInput("findings", FindingDraft.class);
    }

    private ReflectionResult reflect(String code, List<FindingDraft> currentFindings) {
        var resp = claudeClient.callWithTool(
            PromptTemplates.reflectPrompt(code, currentFindings),
            toolSchemas.submitReflection(),
            "submit_reflection"
        );
        return resp.extractToolInputAs(ReflectionResult.class);
    }

    private List<FindingDraft> refine(String code, List<FindingDraft> currentFindings, ReflectionResult reflection) {
        var resp = claudeClient.callWithTool(
            PromptTemplates.refinePrompt(code, currentFindings, reflection),
            toolSchemas.submitFindings(),
            "submit_findings"
        );
        return resp.extractToolInput("findings", FindingDraft.class);
    }

    private DetectedSmell toDetectedSmell(String filePath, FindingDraft draft, int lineCount) {
        int line = Math.max(1, Math.min(draft.lineStart(), lineCount));
        return new DetectedSmell(
            filePath,
            line,
            draft.type(),
            severityOrDefault(draft.severity()),
            draft.message(),
            draft.suggestion(),
            source().name(),
            draft.confidence()
        );
    }

    private Severity severityOrDefault(String raw) {
        if (raw == null) {
            return Severity.INFO;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unbekannter Severity-Wert '{}' vom Modell, nutze INFO", raw);
            return Severity.INFO;
        }
    }

    private int estimateTokens(String code, int reflectRounds) {
        int codeTokens = code.length() / 4;
        int promptOverhead = 500;
        int callCount = 1 + (reflectRounds * 2);
        return (codeTokens + promptOverhead) * callCount;
    }
}