# Backlog – Automatischer Code-Reviewer

Vollständiger Product Backlog zum Projektplan (siehe `Recherche_und_Planung_Code-Reviewer.docx`).
8 Epics, 32 User Stories, 4 Meilensteine. Bereit zum manuellen Import oder automatisiert über
`setup_github_project.sh`.

**Format je Story:** `Als <Rolle> möchte ich <Ziel>, damit <Nutzen>.` · Labels · Meilenstein

**Prioritäten (MoSCoW):** `must` = MVP-kritisch · `should` = wichtig, aber verschiebbar ·
`could` = Stretch-Goal (zuerst gestrichen, wenn Zeit fehlt)

---

## Meilensteine

| Meilenstein | Zeitraum | Fokus |
|---|---|---|
| M1 – Fundament | Woche 1 | Setup, Skeleton, erste API-Anbindung |
| M2 – Core Engine | Woche 2 | Reflection-Agent, statische Analyse, Persistenz |
| M3 – Web-Frontend | Woche 3 | Upload-UI, Monaco, Markierung, Übersicht |
| M4 – Plugin & Politur | Woche 4 | VS-Code-Plugin, Tests, Doku, Demo |

---

## E1 – Projekt-Setup & Infrastruktur
`epic` `area:infra` · Meilenstein: M1

Repo, CI/CD und lokale Entwicklungsumgebung stehen, damit alle drei Personen ab Tag 1 produktiv arbeiten können.

1. **US1.1** — Als Team möchten wir ein GitHub-Repo mit Branch-Schutzregeln und CI-Pipeline haben, damit Code-Qualität von Anfang an gesichert ist.
   `area:infra` `prio:must`
2. **US1.2** — Als Entwickler:in möchte ich ein lauffähiges Spring-Boot-Grundgerüst mit Docker Compose (inkl. Postgres) haben, damit ich lokal sofort entwickeln kann.
   `area:backend` `prio:must`
3. **US1.3** — Als Entwickler:in möchte ich ein Vite/React-Frontend-Grundgerüst haben, damit die UI-Entwicklung parallel starten kann.
   `area:frontend` `prio:must`
4. **US1.4** — Als Team möchten wir Coding-Konventionen (Checkstyle/Prettier) und ein PR-Template definieren, damit Reviews im Team konsistent ablaufen.
   `area:infra` `prio:should`
5. **US1.5** — Als Team möchten wir Secrets (DB-Zugangsdaten, künftig LLM-API-Key) über Umgebungsvariablen statt hartkodiert in versionierten Configs verwalten, damit keine Zugangsdaten im Repo landen.
   `area:infra` `prio:should` ✅ erledigt (`application.yml` nutzt `${DB_USERNAME:...}` etc.)

## E2 – Backend-Grundgerüst & Datenmodell
`epic` `area:backend` · Meilenstein: M1

1. **US2.1** — Als Entwickler:in möchte ich die Entitäten `Project`, `ReviewRun` und `Finding` als JPA-Modelle mit Flyway-Migration anlegen, damit Reviews persistiert werden können.
   `area:backend` `prio:must`
2. **US2.2** — Als Nutzer:in möchte ich über eine REST-API ein Projekt anlegen können, damit ich Code dafür hochladen kann.
   `area:backend` `prio:must`
3. **US2.3** — Als Entwickler:in möchte ich einen Health-/Status-Endpunkt haben, damit Deployment und Monitoring einfach sind.
   `area:backend` `prio:could` ✅ erledigt (Spring Boot Actuator, `/actuator/health`)
4. **US2.4** — Als Entwickler:in möchte ich zentrales Error-Handling (`@RestControllerAdvice`) haben, damit alle Controller einheitliche Fehler-JSONs liefern statt Stacktraces zu leaken.
   `area:backend` `prio:should` ✅ erledigt (`GlobalExceptionHandler`)

## E3 – Code-Ingestion
`epic` `area:backend` · Meilenstein: M1–M2

1. **US3.1** — Als Nutzer:in möchte ich eine einzelne Datei hochladen können, damit ich schnell einen kurzen Codeausschnitt prüfen lassen kann.
   `area:backend` `prio:must` ✅ erledigt (`POST /api/projects/{id}/review-runs`, `.java`-Upload)
2. **US3.2** — Als Nutzer:in möchte ich ein ganzes Projekt als ZIP hochladen können, damit ich mehrere Dateien im Kontext prüfen lassen kann.
   `area:backend` `prio:must` ✅ erledigt (ZIP-Extraktion mit Zip-Slip- & Zip-Bomb-Schutz)
3. **US3.3** — Als Nutzer:in möchte ich ein öffentliches Git-Repository per URL importieren können, damit ich keine manuellen Uploads brauche.
   `area:backend` `prio:could` (Stretch) ✅ erledigt (`POST /review-runs/from-git`, JGit-Shallow-Clone, https-only + SSRF-Guard gegen private/loopback Hosts)
4. **US3.4** — Als Nutzer:in möchte ich eine klare Fehlermeldung bekommen, wenn Dateiformat oder -größe nicht unterstützt werden, damit ich weiß, was ich anpassen muss.
   `area:backend` `prio:should` ✅ erledigt (400 mit Klartext-Message für Format/Größe/leere Datei/ungültige ZIP-Einträge)

## E4 – Statische Analyse-Engine
`epic` `area:backend` · Meilenstein: M2

1. **US4.1** — Als Entwickler:in möchte ich Java-Quellcode per JavaParser in einen AST umwandeln, damit strukturelle Prüfungen möglich sind.
   `area:backend` `prio:must` ✅ erledigt (`GET /review-runs/{id}/ast`, `JavaParserAstService`, Type-/Methodenzahl je Datei)
2. **US4.2** — Als Nutzer:in möchte ich automatisch auf gängige Code-Smells (lange Methoden, tiefe Verschachtelung, ungenutzte Variablen) hingewiesen werden, damit ich offensichtliche Probleme sofort sehe.
   `area:backend` `prio:must` ✅ erledigt (`GET /review-runs/{id}/smells`: `LongMethodDetector`, `DeepNestingDetector`, `UnusedVariableDetector`)
3. **US4.3** — Als Entwickler:in möchte ich die Findings der statischen Analyse in einem einheitlichen Format ausgeben, damit sie mit den LLM-Findings zusammengeführt werden können.
   `area:backend` `prio:must` ✅ erledigt (`POST`/`GET /review-runs/{id}/findings`, persistiert als `Finding`-Entities, reanalysierbar ohne Duplikate)

## E5 – Reflection-Agent / LLM-Review-Engine
`epic` `area:llm` · Meilenstein: M2

1. **US5.1** — Als Nutzer:in möchte ich ein initiales KI-Review meines Codes erhalten (Generate-Schritt), damit ich schnelles Feedback bekomme.
   `area:llm` `prio:must`
2. **US5.2** — Als Nutzer:in möchte ich, dass die KI ihr eigenes Review kritisch prüft (Reflect-Schritt), damit Fehlalarme reduziert werden.
   `area:llm` `prio:must`
3. **US5.3** — Als Nutzer:in möchte ich ein finales, verfeinertes Review erhalten (Refine-Schritt), damit ich nur relevante, geprüfte Hinweise sehe.
   `area:llm` `prio:must`
4. **US5.4** — Als Entwickler:in möchte ich die LLM-Antwort strukturiert (Datei, Zeilen, Kategorie, Schweregrad, Begründung) über Function-Calling erhalten, damit sie automatisiert weiterverarbeitet werden kann.
   `area:llm` `prio:must`
5. **US5.5** — Als Entwickler:in möchte ich LLM-Aufrufe cachen bzw. begrenzen, damit Kosten und Latenz kontrollierbar bleiben.
   `area:llm` `prio:should`

## E6 – Web-Frontend
`epic` `area:frontend` · Meilenstein: M3

1. **US6.1** — Als Nutzer:in möchte ich mein Projekt bzw. meine Datei über die Web-UI hochladen können, damit ich ohne Kommandozeile arbeiten kann.
   `area:frontend` `prio:must`
2. **US6.2** — Als Nutzer:in möchte ich meinen Code im Monaco-Editor mit farblich markierten Problemzeilen sehen, damit ich Probleme sofort lokalisieren kann.
   `area:frontend` `prio:must`
3. **US6.3** — Als Nutzer:in möchte ich beim Hovern über eine Markierung Kategorie, Schweregrad, Begründung und Verbesserungsvorschlag sehen, damit ich direkt handeln kann.
   `area:frontend` `prio:must`
4. **US6.4** — Als Nutzer:in möchte ich alle Findings in einer filterbaren Liste sehen (nach Schweregrad/Kategorie), damit ich Prioritäten setzen kann.
   `area:frontend` `prio:should`
5. **US6.5** — Als Nutzer:in möchte ich frühere Reviews meines Projekts einsehen können, damit ich Fortschritt nachvollziehen kann.
   `area:frontend` `prio:could`

## E7 – VS-Code-Plugin
`epic` `area:vscode` · Meilenstein: M4

1. **US7.1** — Als Entwickler:in möchte ich per Command Palette „Review starten“ aus VS Code auslösen können, damit ich meinen Editor nicht verlassen muss.
   `area:vscode` `prio:must` ✅ erledigt (`codeReviewer.startReview`, Upload + Analyse der aktiven `.java`-Datei über bestehende Backend-API)
2. **US7.2** — Als Entwickler:in möchte ich Findings als native Diagnostics (Squiggly Lines) im Editor sehen, damit sie sich wie gewohnte Fehler-/Warnhinweise verhalten.
   `area:vscode` `prio:must` ✅ erledigt (`DiagnosticCollection`, Severity→Error/Warning/Information gemappt, pro Zeile)
3. **US7.3** — Als Entwickler:in möchte ich per Hover die Begründung und den Vorschlag sehen, damit ich ohne Kontextwechsel weiterarbeiten kann.
   `area:vscode` `prio:should` ✅ erledigt (custom `HoverProvider`, zeigt Kategorie/Severity/Begründung/Vorschlag als Markdown)
4. **US7.4** — Als Entwickler:in möchte ich einen Vorschlag per Quick-Fix direkt übernehmen können, damit Korrekturen schneller gehen.
   `area:vscode` `prio:could` (Stretch)

## E8 – Qualitätssicherung & Deployment
`epic` `area:infra` · Meilenstein: M4

1. **US8.1** — Als Team möchten wir automatisierte Backend-Tests (JUnit/Mockito) für die Kernlogik haben, damit Regressionen früh auffallen.
   `area:infra` `prio:must`
2. **US8.2** — Als Team möchten wir die Anwendung per Docker Compose lokal und für die Demo deployen können, damit die Präsentation reibungslos läuft.
   `area:infra` `prio:must`
3. **US8.3** — Als Team möchten wir eine README mit Architekturüberblick und Setup-Anleitung haben, damit neue Mitglieder oder Dozent:innen das Projekt verstehen.
   `area:infra` `prio:should`
4. **US8.4** — Als Team möchten wir die Abschlusspräsentation anhand eines Beispielprojekts vorbereiten, damit die Kernfeatures überzeugend gezeigt werden.
   `area:infra` `prio:must`

---

## Labels (Farben siehe `setup_github_project.sh`)

`epic` · `user-story` · `area:backend` · `area:frontend` · `area:vscode` · `area:llm` · `area:infra` ·
`prio:must` · `prio:should` · `prio:could`

## Import

- **Automatisch:** `gh auth login` einmalig ausführen, danach `./setup_github_project.sh <owner>/<repo>` starten – legt Labels, Meilensteine, 8 Epic-Issues und 32 Story-Issues (mit Verweis auf ihr Epic) an.
- **Manuell:** Inhalte oben direkt als Issues in GitHub Projects anlegen, Epics per Task-Liste im Issue-Body mit ihren Stories verlinken (GitHub kennt keine native Epic-Hierarchie).
