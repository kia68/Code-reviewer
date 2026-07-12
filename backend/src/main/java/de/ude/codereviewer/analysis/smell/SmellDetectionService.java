package de.ude.codereviewer.analysis.smell;

import de.ude.codereviewer.analysis.ast.AstParserService;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SmellDetectionService {

    private final AstParserService astParserService;
    private final List<SmellDetector> detectors;

    public SmellDetectionService(AstParserService astParserService, List<SmellDetector> detectors) {
        this.astParserService = astParserService;
        this.detectors = detectors;
    }

    public SmellReport detectSmells(Path sourceDir) {
        List<DetectedSmell> smells = astParserService.parseCompilationUnits(sourceDir).stream()
                .flatMap(parsed -> detectors.stream().flatMap(detector -> detector.detect(
                                parsed.relativePath(), parsed.unit())
                        .stream()))
                .toList();
        return new SmellReport(smells);
    }
}
