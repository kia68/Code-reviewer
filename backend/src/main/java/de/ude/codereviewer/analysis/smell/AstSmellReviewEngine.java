package de.ude.codereviewer.analysis.smell;

import de.ude.codereviewer.analysis.ReviewEngine;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter, der die bestehende SmellDetectionService-Logik
 * als ReviewEngine verfügbar macht.
 */
@Component
@RequiredArgsConstructor
public class AstSmellReviewEngine implements ReviewEngine {

    private final SmellDetectionService smellDetectionService;

    @Override
    public List<DetectedSmell> analyze(Path sourcePath) {
        return smellDetectionService.detectSmells(sourcePath).smells();
    }

    @Override
    public FindingSource source() {
        return FindingSource.AST;
    }
}