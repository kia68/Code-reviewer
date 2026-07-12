package de.ude.codereviewer.analysis.smell;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnusedVariableDetectorTest {

    private final UnusedVariableDetector detector = new UnusedVariableDetector();

    @Test
    void shouldFlagUnusedLocalVariable() {
        CompilationUnit unit = StaticJavaParser.parse("class C { void m() { int unused = 5; } }");

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).hasSize(1);
        assertThat(smells.get(0).category()).isEqualTo("UNUSED_VARIABLE");
        assertThat(smells.get(0).description()).contains("unused");
    }

    @Test
    void shouldNotFlagUsedVariable() {
        CompilationUnit unit =
                StaticJavaParser.parse("class C { void m() { int x = 5; System.out.println(x); } }");

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).isEmpty();
    }

    @Test
    void shouldNotFlagTryWithResourcesVariable() {
        CompilationUnit unit = StaticJavaParser.parse(
                """
                class C {
                    void m() throws Exception {
                        try (AutoCloseable resource = () -> {}) {
                            System.out.println("using resource implicitly");
                        }
                    }
                }
                """);

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).isEmpty();
    }
}
