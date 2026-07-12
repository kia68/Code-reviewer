package de.ude.codereviewer.analysis.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JavaParserAstService implements AstParserService {

    private final JavaParser javaParser;

    public JavaParserAstService() {
        ParserConfiguration configuration =
                new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.javaParser = new JavaParser(configuration);
    }

    @Override
    public AstParseReport parseSourceDirectory(Path sourceDir) {
        List<ParsedFileInfo> results =
                parseAll(sourceDir).stream().map(this::toParsedFileInfo).toList();
        return new AstParseReport(results);
    }

    @Override
    public List<ParsedCompilationUnit> parseCompilationUnits(Path sourceDir) {
        return parseAll(sourceDir).stream()
                .filter(outcome -> outcome.unit() != null)
                .map(outcome -> new ParsedCompilationUnit(outcome.relativePath(), outcome.unit()))
                .toList();
    }

    private List<FileParseOutcome> parseAll(Path sourceDir) {
        if (!Files.isDirectory(sourceDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quellverzeichnis nicht gefunden.");
        }

        List<FileParseOutcome> results = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            List<Path> javaFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(".java"))
                    .sorted()
                    .toList();

            for (Path file : javaFiles) {
                results.add(parseFile(sourceDir, file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Quellverzeichnis konnte nicht gelesen werden.", e);
        }

        return results;
    }

    private FileParseOutcome parseFile(Path baseDir, Path file) {
        String relativePath = baseDir.relativize(file).toString();

        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            return new FileParseOutcome(relativePath, null, "Datei konnte nicht gelesen werden: " + e.getMessage());
        }

        ParseResult<CompilationUnit> result = javaParser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            String message =
                    result.getProblems().stream().map(Problem::getMessage).collect(Collectors.joining("; "));
            return new FileParseOutcome(relativePath, null, message);
        }

        return new FileParseOutcome(relativePath, result.getResult().get(), null);
    }

    private ParsedFileInfo toParsedFileInfo(FileParseOutcome outcome) {
        if (outcome.unit() == null) {
            return new ParsedFileInfo(outcome.relativePath(), false, 0, 0, outcome.errorMessage());
        }
        int typeCount = outcome.unit().findAll(TypeDeclaration.class).size();
        int methodCount = outcome.unit().findAll(MethodDeclaration.class).size();
        return new ParsedFileInfo(outcome.relativePath(), true, typeCount, methodCount, null);
    }

    private record FileParseOutcome(String relativePath, CompilationUnit unit, String errorMessage) { }
}
