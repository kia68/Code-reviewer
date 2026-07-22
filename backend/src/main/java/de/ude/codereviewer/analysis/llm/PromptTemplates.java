package de.ude.codereviewer.analysis.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ude.codereviewer.analysis.llm.dto.FindingDraft;
import de.ude.codereviewer.analysis.llm.dto.ReflectionResult;
import java.util.List;

public final class PromptTemplates {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Erklärt Claude die drei zulässigen Schweregrade, damit es
     * konsistent mit der bestehenden AST-Analyse (Severity-Enum:
     * INFO, WARNING, CRITICAL) bewertet.
     */
    private static final String SEVERITY_GUIDE = """
        Verwende für 'severity' AUSSCHLIESSLICH diese drei Stufen:
        - INFO: Stilistischer Hinweis oder kleinere Verbesserung, keine Handlungspflicht
        - WARNING: Sollte behoben werden, aber nicht dringend (z.B. Code-Smell, Wartbarkeitsproblem)
        - CRITICAL: Logikfehler, Sicherheitsproblem oder schwerwiegender Bug, der behoben werden MUSS
        """;

    /**
     * Empfohlene Kategorie-Bezeichner für 'type', damit LLM-Findings
     * und AST-Findings im Frontend unter denselben Kategorienamen
     * erscheinen. Passe diese Liste an eure tatsächliche AST-Taxonomie an.
     */
    private static final String CATEGORY_GUIDE = """
        Verwende für 'type' bevorzugt eine dieser Kategorien (oder eine
        vergleichbare, falls keine passt):
        LONG_METHOD, GOD_CLASS, DUPLICATE_CODE, LOGIC_ERROR,
        SECURITY_ISSUE, PERFORMANCE_ISSUE, STYLE_VIOLATION,
        NAMING_ISSUE, DEAD_CODE
        """;

    private PromptTemplates() {
        // reine Utility-Klasse, keine Instanziierung vorgesehen
    }

    /**
     * Phase 1: GENERATE - initiale Analyse des Codes.
     */
    public static String generatePrompt(String code) {
        return """
            Du bist ein erfahrener Java Code-Reviewer.
            Analysiere den folgenden Code auf Code-Smells, Logikfehler,
            Security-Probleme, Performance-Probleme und Stil-Verstöße.

            %s

            %s

            Gib für jeden Fund eine Confidence (0.0 - 1.0) an, wie sicher
            du bist, dass es sich tatsächlich um ein Problem handelt.

            Rufe ausschließlich das Tool 'submit_findings' auf.
            Falls der Code keine nennenswerten Probleme aufweist,
            rufe das Tool trotzdem mit einer leeren findings-Liste auf.

            CODE:
            ```java
            %s
            ```
            """.formatted(SEVERITY_GUIDE, CATEGORY_GUIDE, code);
    }

    /**
     * Phase 2: REFLECT - kritische Prüfung der eigenen initialen Findings.
     */
    public static String reflectPrompt(String code, List<FindingDraft> findings) {
        return """
            Du hast in einem vorherigen Schritt folgende Findings zu
            diesem Code erstellt (Index beginnt bei 0):
            %s

            Prüfe JEDEN Fund kritisch und einzeln:
            - Ist er tatsächlich korrekt, oder handelt es sich um einen False Positive?
            - Ist der Schweregrad angemessen?
            %s

            - Gibt es offensichtliche Probleme im Code, die du in der
              ursprünglichen Analyse übersehen hast? Liste diese unter
              'missedIssues' als kurze Freitext-Beschreibungen auf.

            Sei streng und selbstkritisch. Ein guter Reviewer erkennt
            eigene Fehleinschätzungen.

            Rufe ausschließlich das Tool 'submit_reflection' auf.
            Für JEDEN Index aus der obigen Liste muss genau ein
            Reflection-Eintrag existieren.

            CODE:
            ```java
            %s
            ```
            """.formatted(toJson(findings), SEVERITY_GUIDE, code);
    }

    /**
     * Phase 3: REFINE - finale, bereinigte Findings-Liste unter
     * Berücksichtigung der Reflexion aus Phase 2.
     */
    public static String refinePrompt(String code, List<FindingDraft> initial, ReflectionResult reflection) {
        return """
            Ursprüngliche Findings (Index beginnt bei 0):
            %s

            Deine kritische Reflexion dazu:
            %s

            Erstelle nun die FINALE, bereinigte Findings-Liste nach
            folgenden Regeln:
            - Entferne alle Findings, die in der Reflexion als
              FALSE_POSITIVE markiert wurden.
            - Übernimm bei SEVERITY_ADJUSTED die revisedSeverity als
              neuen Schweregrad.
            - Übernimm CONFIRMED-Findings unverändert.
            - Ergänze neue Findings für jeden Eintrag aus 'missedIssues',
              mit passendem type, severity, lineStart und message.

            %s

            %s

            Rufe ausschließlich das Tool 'submit_findings' mit der
            finalen, bereinigten Liste auf.

            CODE:
            ```java
            %s
            ```
            """.formatted(
                toJson(initial),
                toJson(reflection),
                SEVERITY_GUIDE,
                CATEGORY_GUIDE,
                code
            );
    }

    /**
     * Serialisiert Prompt-Daten (Findings, Reflections) als JSON,
     * damit Claude sie in einem für das Modell gut lesbaren,
     * strukturierten Format zur Verfügung hat.
     */
    private static String toJson(Object obj) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Konnte Prompt-Daten nicht zu JSON serialisieren", e);
        }
    }
}