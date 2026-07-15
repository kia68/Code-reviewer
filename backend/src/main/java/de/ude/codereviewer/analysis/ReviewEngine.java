package de.ude.codereviewer.analysis;

import de.ude.codereviewer.analysis.smell.DetectedSmell;
import java.nio.file.Path;
import java.util.List;

/**
 * Gemeinsame Abstraktion für alle Analyse-Engines (AST-basiert und LLM-basiert).
 * Jede Implementierung analysiert den Quellcode unter dem gegebenen Pfad
 * und liefert eine Liste von Findings im gemeinsamen DetectedSmell-Format,
 * damit sie einheitlich zu Finding-Entities gemappt werden können.
 */
public interface ReviewEngine {

    List<DetectedSmell> analyze(Path sourcePath);

    /**
     * Kennzeichnet, woher ein Finding stammt – wichtig für Frontend-Anzeige
     * und Filterung (z.B. "nur AST-Findings anzeigen").
     */
    FindingSource source();

    enum FindingSource {
        AST, LLM
    }
}