package de.ude.codereviewer.analysis.llm.dto;

import java.util.List;

/**
 * Finales Ergebnis der Refine-Phase im Generate-Reflect-Refine-Zyklus:
 * die bereinigte, endgültige Findings-Liste sowie eine optionale
 * menschenlesbare Zusammenfassung des gesamten Reviews.
 */
public record RefinedResult(
    List<FindingDraft> findings,
    String summary
) {
    /**
     * Compact Constructor: schützt vor NullPointerExceptions,
     * falls Claude eines der Felder leer lässt, und macht die
     * Liste defensiv unveränderlich.
     */
    public RefinedResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}