package de.ude.codereviewer.analysis.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import de.ude.codereviewer.review.model.Severity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnusedVariableDetector implements SmellDetector {

    @Override
    public List<DetectedSmell> detect(String relativeFilePath, CompilationUnit unit) {
        List<DetectedSmell> smells = new ArrayList<>();
        for (VariableDeclarationExpr declExpr : unit.findAll(VariableDeclarationExpr.class)) {
            if (isTryWithResourcesVariable(declExpr)) {
                continue;
            }

            CallableDeclaration<?> enclosingMethod =
                    declExpr.findAncestor(CallableDeclaration.class).orElse(null);
            if (enclosingMethod == null) {
                continue;
            }

            for (VariableDeclarator declarator : declExpr.getVariables()) {
                String name = declarator.getNameAsString();
                long usageCount = enclosingMethod.findAll(NameExpr.class).stream()
                        .filter(nameExpr -> nameExpr.getNameAsString().equals(name))
                        .count();
                if (usageCount == 0) {
                    int line = declarator.getBegin().map(p -> p.line).orElse(0);
                    smells.add(new DetectedSmell(
                            relativeFilePath,
                            line,
                            "UNUSED_VARIABLE",
                            Severity.INFO,
                            "Lokale Variable '" + name + "' wird nie verwendet.",
                            "Ungenutzte Variable entfernen."));
                }
            }
        }
        return smells;
    }

    // try-with-resources variables are used implicitly for auto-closing, not via NameExpr references.
    private boolean isTryWithResourcesVariable(VariableDeclarationExpr declExpr) {
        Node parent = declExpr.getParentNode().orElse(null);
        return parent instanceof TryStmt tryStmt && tryStmt.getResources().contains(declExpr);
    }
}
