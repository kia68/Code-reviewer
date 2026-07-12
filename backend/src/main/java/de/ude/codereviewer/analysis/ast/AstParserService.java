package de.ude.codereviewer.analysis.ast;

import java.nio.file.Path;

public interface AstParserService {

    // Parses every .java file under sourceDir into an AST and reports per-file success/failure.
    AstParseReport parseSourceDirectory(Path sourceDir);
}
