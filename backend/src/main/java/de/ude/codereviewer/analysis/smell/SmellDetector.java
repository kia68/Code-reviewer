package de.ude.codereviewer.analysis.smell;

import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

public interface SmellDetector {

    List<DetectedSmell> detect(String relativeFilePath, CompilationUnit unit);
}
