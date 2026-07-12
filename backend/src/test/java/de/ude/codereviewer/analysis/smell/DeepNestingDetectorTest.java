package de.ude.codereviewer.analysis.smell;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import de.ude.codereviewer.analysis.config.AnalysisProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepNestingDetectorTest {

    private final DeepNestingDetector detector = new DeepNestingDetector(new AnalysisProperties(5, 3));

    @Test
    void shouldFlagNestingBeyondThreshold() {
        CompilationUnit unit = StaticJavaParser.parse("""
                class C {
                    void m(boolean a, boolean b, boolean c, boolean d) {
                        if (a) {
                            if (b) {
                                if (c) {
                                    if (d) {
                                        System.out.println("deep");
                                    }
                                }
                            }
                        }
                    }
                }
                """);

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).hasSize(1);
        assertThat(smells.get(0).category()).isEqualTo("DEEP_NESTING");
    }

    @Test
    void shouldNotFlagNestingAtThreshold() {
        CompilationUnit unit = StaticJavaParser.parse("""
                class C {
                    void m(boolean a, boolean b, boolean c) {
                        if (a) {
                            if (b) {
                                if (c) {
                                    System.out.println("ok");
                                }
                            }
                        }
                    }
                }
                """);

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).isEmpty();
    }

    @Test
    void shouldNotTreatElseIfChainAsDeepNesting() {
        CompilationUnit unit = StaticJavaParser.parse("""
                class C {
                    void m(int x) {
                        if (x == 1) {
                            System.out.println("one");
                        } else if (x == 2) {
                            System.out.println("two");
                        } else if (x == 3) {
                            System.out.println("three");
                        } else if (x == 4) {
                            System.out.println("four");
                        } else {
                            System.out.println("other");
                        }
                    }
                }
                """);

        List<DetectedSmell> smells = detector.detect("C.java", unit);

        assertThat(smells).isEmpty();
    }
}
