package de.ude.codereviewer.analysis.llm;

import de.ude.codereviewer.analysis.ReviewEngine;
import de.ude.codereviewer.analysis.llm.cache.LlmReviewCache;
import de.ude.codereviewer.analysis.llm.dto.FindingDraft;
import de.ude.codereviewer.analysis.llm.dto.ReflectionResult;
import de.ude.codereviewer.analysis.llm.ratelimit.TokenBudgetGuard;
import de.ude.codereviewer.analysis.smell.DetectedSmell;
import de.ude.codereviewer.analysis.review.model.Severity;
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

    @Override
    public List<DetectedSmell> analyze(Path sourcePath) {
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(javaFile -> analyzeFile(javaFile).stream())
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte Quellverzeichnis nicht durchsuchen: " + sourcePath, e);
        }
    }

    @Override
    public FindingSource source() {
        return FindingSource.LLM;
    }

    private List<DetectedSmell> analyzeFile(Path javaFile) {
        String relativePath = javaFile.toString();
        String content;
        try {
            content = Files.readString(javaFile);
        } catch (IOException e) {
            log.warn("Konnte Datei nicht lesen, überspringe LLM-Review: {}", relativePath, e);
            return List.of();
        }

        // Sehr kleine/leere Dateien überspringen, um unnötige API-Calls zu vermeiden
        if (content.isBlank() || content.lines().count() < 3) {
            return List.of();
        }

        String cacheKey = cache.buildKey(content, PROMPT_VERSION, properties.model());

        List<FindingDraft> refinedFindings = cache.get(cacheKey, () -> runCycle(content));

        return refinedFindings.stream()
            .map(draft -> toDetectedSmell(relativePath, draft))
            .toList();
    }

    private List<FindingDraft> runCycle(String code) {
        budgetGuard.checkAndReserve(estimateTokens(code));

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

    private DetectedSmell toDetectedSmell(String filePath, FindingDraft draft) {
        return new DetectedSmell(
            filePath,
            draft.lineStart(),
            draft.type(),
            Severity.valueOf(draft.severity()),
            draft.message(),
            draft.suggestion()
        );
    }

    private int estimateTokens(String code) {
        // grobe Heuristik: ~4 Zeichen pro Token, x3 für Generate+Reflect+Refine
        return (code.length() / 4) * 3;
    }
}