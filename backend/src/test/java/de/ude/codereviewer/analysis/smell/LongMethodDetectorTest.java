package de.ude.codereviewer.analysis.smell;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import de.ude.codereviewer.analysis.config.AnalysisProperties;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LongMethodDetectorTest {

    private final LongMethodDetector detector = new LongMethodDetector(new AnalysisProperties(5, 3));

    @Test
    void shouldFlagMethodExceedingLineThreshold() {
        String longBody = Stream.generate(() -> "        System.out.println(\"x\");")
                .limit(10)
                .reduce("", (a, b) -> a + "\n" + b);
        CompilationUnit unit = StaticJavaParser.parse(
                "class C { void longMethod() {" + longBody + "\n    } }");

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).hasSize(1);
        assertThat(smells.get(0).category()).isEqualTo("LONG_METHOD");
        assertThat(smells.get(0).filePath()).isEqualTo("C.java");
    }

    @Test
    void shouldNotFlagShortMethod() {
        CompilationUnit unit = StaticJavaParser.parse("class C { void shortMethod() { int x = 1; } }");

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).isEmpty();
    }
}
