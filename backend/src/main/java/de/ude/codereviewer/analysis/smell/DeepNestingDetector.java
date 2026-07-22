package de.ude.codereviewer.analysis.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import de.ude.codereviewer.analysis.config.AnalysisProperties;
import de.ude.codereviewer.review.model.Severity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeepNestingDetector implements SmellDetector {

    private final AnalysisProperties properties;

    public DeepNestingDetector(AnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<DetectedSmell> detect(String relativeFilePath, CompilationUnit unit) {
        List<DetectedSmell> smells = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            callable.getChildNodes().stream()
                    .filter(BlockStmt.class::isInstance)
                    .map(BlockStmt.class::cast)
                    .findFirst()
                    .ifPresent(body -> {
                        DepthProbe deepest = depthOf(body, 0, callable);
                        if (deepest.depth() > properties.maxNestingDepth()) {
                            smells.add(new DetectedSmell(
                                    relativeFilePath,
                                    deepest.line(),
                                    "DEEP_NESTING",
                                    Severity.WARNING,
                                    "Methode '" + callable.getNameAsString() + "' verschachtelt Kontrollstrukturen "
                                            + deepest.depth() + " Ebenen tief (Grenzwert: "
                                            + properties.maxNestingDepth() + ").",
                                    "Verschachtelte Blöcke in eigene Methoden extrahieren oder Guard-Clauses verwenden.",
                                    "AST", null));
                        }
                    });
        }
        return smells;
    }

    private DepthProbe depthOf(Statement stmt, int depth, CallableDeclaration<?> enclosing) {
        int fallbackLine = stmt.getBegin().map(p -> p.line).orElse(
                enclosing.getBegin().map(p -> p.line).orElse(0));

        if (stmt instanceof BlockStmt block) {
            DepthProbe deepest = new DepthProbe(depth, fallbackLine);
            for (Statement s : block.getStatements()) {
                DepthProbe candidate = depthOf(s, depth, enclosing);
                if (candidate.depth() > deepest.depth()) {
                    deepest = candidate;
                }
            }
            return deepest;
        }
        if (stmt instanceof IfStmt ifStmt) {
            DepthProbe thenProbe = depthOf(ifStmt.getThenStmt(), depth + 1, enclosing);
            DepthProbe deepest = thenProbe;
            if (ifStmt.getElseStmt().isPresent()) {
                Statement elseStmt = ifStmt.getElseStmt().get();
                int elseDepth = (elseStmt instanceof IfStmt) ? depth : depth + 1;
                DepthProbe elseProbe = depthOf(elseStmt, elseDepth, enclosing);
                if (elseProbe.depth() > deepest.depth()) {
                    deepest = elseProbe;
                }
            }
            return deepest;
        }
        if (stmt instanceof ForStmt forStmt) {
            return depthOf(forStmt.getBody(), depth + 1, enclosing);
        }
        if (stmt instanceof ForEachStmt forEachStmt) {
            return depthOf(forEachStmt.getBody(), depth + 1, enclosing);
        }
        if (stmt instanceof WhileStmt whileStmt) {
            return depthOf(whileStmt.getBody(), depth + 1, enclosing);
        }
        if (stmt instanceof DoStmt doStmt) {
            return depthOf(doStmt.getBody(), depth + 1, enclosing);
        }
        if (stmt instanceof SynchronizedStmt syncStmt) {
            return depthOf(syncStmt.getBody(), depth + 1, enclosing);
        }
        if (stmt instanceof SwitchStmt switchStmt) {
            DepthProbe deepest = new DepthProbe(depth, fallbackLine);
            for (SwitchEntry entry : switchStmt.getEntries()) {
                for (Statement s : entry.getStatements()) {
                    DepthProbe candidate = depthOf(s, depth + 1, enclosing);
                    if (candidate.depth() > deepest.depth()) {
                        deepest = candidate;
                    }
                }
            }
            return deepest;
        }
        if (stmt instanceof TryStmt tryStmt) {
            DepthProbe deepest = depthOf(tryStmt.getTryBlock(), depth + 1, enclosing);
            for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                DepthProbe candidate = depthOf(catchClause.getBody(), depth + 1, enclosing);
                if (candidate.depth() > deepest.depth()) {
                    deepest = candidate;
                }
            }
            if (tryStmt.getFinallyBlock().isPresent()) {
                DepthProbe candidate = depthOf(tryStmt.getFinallyBlock().get(), depth + 1, enclosing);
                if (candidate.depth() > deepest.depth()) {
                    deepest = candidate;
                }
            }
            return deepest;
        }
        return new DepthProbe(depth, fallbackLine);
    }

    private record DepthProbe(int depth, int line) { }
}
