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
    private final ClaudeToolSchemas toolSchemas;
    private final LlmReviewCache cache;
    private final TokenBudgetGuard budgetGuard;
    private final LlmProperties properties;

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
        budgetGuard.checkBudget(estimateTokens(code));

        List<FindingDraft> initial = generate(code);
        ReflectionResult reflection = reflect(code, initial);
        return refine(code, initial, reflection);
    }

    private List<FindingDraft> generate(String code) {
        String prompt = PromptTemplates.generatePrompt(code);
        var response = claudeClient.callWithTool(prompt, toolSchemas.submitFindings(), "submit_findings");
        budgetGuard.recordActualUsage(response.usage().total());
        return response.extractToolInput("findings", FindingDraft.class);
    }

    private ReflectionResult reflect(String code, List<FindingDraft> initial) {
        String prompt = PromptTemplates.reflectPrompt(code, initial);
        var response = claudeClient.callWithTool(prompt, toolSchemas.submitReflection(), "submit_reflection");
        budgetGuard.recordActualUsage(response.usage().total());
        return response.extractToolInputAs(ReflectionResult.class);
    }

    private List<FindingDraft> refine(String code, List<FindingDraft> initial, ReflectionResult reflection) {
        String prompt = PromptTemplates.refinePrompt(code, initial, reflection);
        var response = claudeClient.callWithTool(prompt, toolSchemas.submitFindings(), "submit_findings");
        budgetGuard.recordActualUsage(response.usage().total());
        return response.extractToolInput("findings", FindingDraft.class);
    }

    private DetectedSmell toDetectedSmell(String filePath, FindingDraft draft, int lineCount) {
        int line = Math.max(1, Math.min(draft.lineStart(), lineCount));
        return new DetectedSmell(
            filePath,
            line,
            draft.type(),
            Severity.valueOf(draft.severity()),
            draft.message(),
            draft.suggestion(),
            source().name(),
            draft.confidence()
        );
    }

    private int estimateTokens(String code) {
        int codeTokens = code.length() / 4;
        int promptOverhead = 500;
        int perCall = codeTokens + promptOverhead;
        return perCall * 3;
    }
}