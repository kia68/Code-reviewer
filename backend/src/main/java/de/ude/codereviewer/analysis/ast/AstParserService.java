package de.ude.codereviewer.analysis.ast;

import java.nio.file.Path;
import java.util.List;

public interface AstParserService {

    // Parses every .java file under sourceDir into an AST and reports per-file success/failure.
    AstParseReport parseSourceDirectory(Path sourceDir);

    // Same parse pass, but returns the actual AST of every successfully parsed file for further analysis.
    List<ParsedCompilationUnit> parseCompilationUnits(Path sourceDir);
}
