package de.ude.codereviewer.analysis.ast;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserAstServiceTest {

    private final JavaParserAstService service = new JavaParserAstService();

    @Test
    void shouldReportTypeAndMethodCountsForValidFile(@TempDir Path tempDir) throws IOException {
        writeFile(
                tempDir,
                "Greeter.java",
                """
                class Greeter {
                    String greet(String name) {
                        return "Hello, " + name;
                    }

                    void sayBye() {
                        System.out.println("Bye");
                    }
                }
                """);

        AstParseReport report = service.parseSourceDirectory(tempDir);

        assertThat(report.files()).hasSize(1);
        ParsedFileInfo file = report.files().get(0);
        assertThat(file.success()).isTrue();
        assertThat(file.typeCount()).isEqualTo(1);
        assertThat(file.methodCount()).isEqualTo(2);
        assertThat(report.successCount()).isEqualTo(1);
        assertThat(report.failureCount()).isZero();
    }

    @Test
    void shouldReportFailureForSyntacticallyInvalidFile(@TempDir Path tempDir) throws IOException {
        writeFile(tempDir, "Broken.java", "class Broken { void oops( { }");

        AstParseReport report = service.parseSourceDirectory(tempDir);

        assertThat(report.files()).hasSize(1);
        ParsedFileInfo file = report.files().get(0);
        assertThat(file.success()).isFalse();
        assertThat(file.errorMessage()).isNotBlank();
        assertThat(report.failureCount()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreNonJavaFiles(@TempDir Path tempDir) throws IOException {
        writeFile(tempDir, "notes.txt", "not java");

        AstParseReport report = service.parseSourceDirectory(tempDir);

        assertThat(report.files()).isEmpty();
    }

    @Test
    void shouldCountNestedTypesAcrossSubdirectories(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("sub"));
        writeFile(tempDir, "A.java", "class A { void m() {} }");
        writeFile(tempDir.resolve("sub"), "B.java", "class B { void m() {} void n() {} }");

        AstParseReport report = service.parseSourceDirectory(tempDir);

        assertThat(report.files()).hasSize(2);
        assertThat(report.successCount()).isEqualTo(2);
    }

    private void writeFile(Path dir, String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }
}
