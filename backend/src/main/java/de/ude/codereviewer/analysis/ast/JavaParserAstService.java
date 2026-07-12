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
        if (!Files.isDirectory(sourceDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quellverzeichnis nicht gefunden.");
        }

        List<ParsedFileInfo> results = new ArrayList<>();
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

        return new AstParseReport(results);
    }

    private ParsedFileInfo parseFile(Path baseDir, Path file) {
        String relativePath = baseDir.relativize(file).toString();

        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            return new ParsedFileInfo(relativePath, false, 0, 0, "Datei konnte nicht gelesen werden: " + e.getMessage());
        }

        ParseResult<CompilationUnit> result = javaParser.parse(content);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            String message =
                    result.getProblems().stream().map(Problem::getMessage).collect(Collectors.joining("; "));
            return new ParsedFileInfo(relativePath, false, 0, 0, message);
        }

        CompilationUnit compilationUnit = result.getResult().get();
        int typeCount = compilationUnit.findAll(TypeDeclaration.class).size();
        int methodCount = compilationUnit.findAll(MethodDeclaration.class).size();
        return new ParsedFileInfo(relativePath, true, typeCount, methodCount, null);
    }
}
