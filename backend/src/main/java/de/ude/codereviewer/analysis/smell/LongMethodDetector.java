package de.ude.codereviewer.analysis.smell;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import de.ude.codereviewer.analysis.config.AnalysisProperties;
import de.ude.codereviewer.review.model.Severity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LongMethodDetector implements SmellDetector {

    private final AnalysisProperties properties;

    public LongMethodDetector(AnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DetectedSmell> detect(String relativeFilePath, CompilationUnit unit) {
        List<DetectedSmell> smells = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            Range range = callable.getRange().orElse(null);
            if (range == null) {
                continue;
            }
            int lineCount = range.end.line - range.begin.line + 1;
            if (lineCount > properties.longMethodLineThreshold()) {
                smells.add(new DetectedSmell(
                        relativeFilePath,
                        range.begin.line,
                        "LONG_METHOD",
                        Severity.WARNING,
                        "Methode '" + callable.getNameAsString() + "' hat " + lineCount
                                + " Zeilen (Grenzwert: " + properties.longMethodLineThreshold() + ").",
                        "Methode in kleinere, fokussierte Methoden aufteilen."));
            }
        }
        return smells;
    }
}
