#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# GitHub Setup Script -- Automatischer Code-Reviewer
#
# Legt Labels, Meilensteine sowie das komplette Backlog
# (8 Epics, 32 User Stories - siehe BACKLOG.md) als Issues
# in einem GitHub-Repo an.
#
# Voraussetzungen:
#   - GitHub CLI (gh) installiert:  https://cli.github.com
#   - Einmalig eingeloggt:          gh auth login
#
# Nutzung:
#   ./setup_github_project.sh <owner>/<repo>
#   Beispiel: ./setup_github_project.sh mein-team/code-reviewer
#
# Hinweis: Labels und Meilensteine sind sicher mehrfach ausfuehrbar
# (werden aktualisiert bzw. uebersprungen). Issues werden bei
# jedem Lauf NEU angelegt -- das Skript daher nur einmal pro
# Repo ausfuehren, oder Issues danach manuell bereinigen.
# ============================================================

REPO="${1:-}"
if [[ -z "$REPO" ]]; then
  echo "Nutzung: $0 <owner>/<repo>"
  exit 1
fi

if ! command -v gh &> /dev/null; then
  echo "GitHub CLI (gh) wurde nicht gefunden. Installation: https://cli.github.com"
  exit 1
fi

if ! gh api user &> /dev/null; then
  echo "Nicht bei der GitHub CLI angemeldet. Bitte zuerst ausfuehren: gh auth login"
  exit 1
fi

echo "==> Repo: $REPO"

# ------------------------------------------------------------
# 1) Labels
# ------------------------------------------------------------
echo "==> Lege Labels an ..."
declare -A LABELS=(
  ["epic"]="5319E7:Grosses Feature-Buendel, in mehrere User Stories zerlegt"
  ["user-story"]="0E8A16:Einzelne, umsetzbare Anforderung"
  ["area:backend"]="1D76DB:Spring-Boot-Backend"
  ["area:frontend"]="FBCA04:React-Web-Frontend"
  ["area:vscode"]="C2E0C6:VS-Code-Plugin"
  ["area:llm"]="D93F0B:Reflection-Agent / Claude-API-Integration"
  ["area:infra"]="BFDADC:CI/CD, Docker, Tooling"
  ["prio:must"]="B60205:MVP-kritisch"
  ["prio:should"]="FBCA04:Wichtig, aber verschiebbar"
  ["prio:could"]="C5DEF5:Stretch-Goal"
)
for name in "${!LABELS[@]}"; do
  color="${LABELS[$name]%%:*}"
  desc="${LABELS[$name]#*:}"
  gh label create "$name" --repo "$REPO" --color "$color" --description "$desc" --force
done

# ------------------------------------------------------------
# 2) Meilensteine
# ------------------------------------------------------------
echo "==> Lege Meilensteine an ..."
create_milestone() {
  local title="$1" desc="$2"
  if gh api "repos/$REPO/milestones" -f title="$title" -f description="$desc" -f state="open" > /dev/null 2>&1; then
    echo "    OK: $title"
  else
    echo "    uebersprungen (existiert vermutlich schon): $title"
  fi
}
create_milestone "M1 - Fundament" "Woche 1: Setup, Skeleton, erste API-Anbindung"
create_milestone "M2 - Core Engine" "Woche 2: Reflection-Agent, statische Analyse, Persistenz"
create_milestone "M3 - Web-Frontend" "Woche 3: Upload-UI, Monaco, Markierung, Uebersicht"
create_milestone "M4 - Plugin & Politur" "Woche 4: VS-Code-Plugin, Tests, Doku, Demo"

# ------------------------------------------------------------
# 3) Epics + User Stories
# ------------------------------------------------------------
declare -A EPIC_NUM=()

create_epic() {
  local key="$1" title="$2" body="$3" milestone="$4"
  echo "==> Epic: $title"
  local url
  url=$(gh issue create --repo "$REPO" --title "$title" --body "$body" \
    --label "epic" --milestone "$milestone")
  EPIC_NUM["$key"]="${url##*/}"
}

create_story() {
  local epic_key="$1" title="$2" body="$3" labels="$4" milestone="$5"
  local epic_ref="${EPIC_NUM[$epic_key]}"
  local full_body
  full_body="$(printf '%s\n\nTeil von Epic #%s.' "$body" "$epic_ref")"
  echo "    Story: $title"
  gh issue create --repo "$REPO" --title "$title" --body "$full_body" \
    --label "user-story,$labels" --milestone "$milestone" > /dev/null
}

# --- E1: Projekt-Setup & Infrastruktur -----------------------
create_epic "E1" "E1 - Projekt-Setup & Infrastruktur" \
"Repo, CI/CD und lokale Entwicklungsumgebung stehen, damit alle drei Personen ab Tag 1 produktiv arbeiten koennen." \
"M1 - Fundament"

create_story "E1" "US1.1 - Repo mit Branch-Schutz & CI-Pipeline" \
"Als Team moechten wir ein GitHub-Repo mit Branch-Schutzregeln und CI-Pipeline haben, damit Code-Qualitaet von Anfang an gesichert ist." \
"area:infra,prio:must" "M1 - Fundament"

create_story "E1" "US1.2 - Spring-Boot-Grundgeruest mit Docker Compose" \
"Als Entwickler:in moechte ich ein lauffaehiges Spring-Boot-Grundgeruest mit Docker Compose (inkl. Postgres) haben, damit ich lokal sofort entwickeln kann." \
"area:backend,prio:must" "M1 - Fundament"

create_story "E1" "US1.3 - Vite/React-Frontend-Grundgeruest" \
"Als Entwickler:in moechte ich ein Vite/React-Frontend-Grundgeruest haben, damit die UI-Entwicklung parallel starten kann." \
"area:frontend,prio:must" "M1 - Fundament"

create_story "E1" "US1.4 - Coding-Konventionen & PR-Template" \
"Als Team moechten wir Coding-Konventionen (Checkstyle/Prettier) und ein PR-Template definieren, damit Reviews im Team konsistent ablaufen." \
"area:infra,prio:should" "M1 - Fundament"

# --- E2: Backend-Grundgeruest & Datenmodell ------------------
create_epic "E2" "E2 - Backend-Grundgeruest & Datenmodell" \
"JPA-Entitaeten, Migrationen und das REST-Grundgeruest stehen als Basis fuer alle weiteren Backend-Epics." \
"M1 - Fundament"

create_story "E2" "US2.1 - JPA-Entitaeten Project/ReviewRun/Finding" \
"Als Entwickler:in moechte ich die Entitaeten Project, ReviewRun und Finding als JPA-Modelle mit Flyway-Migration anlegen, damit Reviews persistiert werden koennen." \
"area:backend,prio:must" "M1 - Fundament"

create_story "E2" "US2.2 - REST-Endpunkt zum Anlegen eines Projekts" \
"Als Nutzer:in moechte ich ueber eine REST-API ein Projekt anlegen koennen, damit ich Code dafuer hochladen kann." \
"area:backend,prio:must" "M1 - Fundament"

create_story "E2" "US2.3 - Health-/Status-Endpunkt" \
"Als Entwickler:in moechte ich einen Health-/Status-Endpunkt haben, damit Deployment und Monitoring einfach sind." \
"area:backend,prio:could" "M1 - Fundament"

# --- E3: Code-Ingestion --------------------------------------
create_epic "E3" "E3 - Code-Ingestion" \
"Quellcode und ganze Projekte lassen sich zuverlaessig in das System laden - die Grundvoraussetzung fuer jeden Review." \
"M1 - Fundament"

create_story "E3" "US3.1 - Einzeldatei-Upload" \
"Als Nutzer:in moechte ich eine einzelne Datei hochladen koennen, damit ich schnell einen kurzen Codeausschnitt pruefen lassen kann." \
"area:backend,prio:must" "M1 - Fundament"

create_story "E3" "US3.2 - ZIP-/Projekt-Upload" \
"Als Nutzer:in moechte ich ein ganzes Projekt als ZIP hochladen koennen, damit ich mehrere Dateien im Kontext pruefen lassen kann." \
"area:backend,prio:must" "M2 - Core Engine"

create_story "E3" "US3.3 - Import per Git-URL (Stretch)" \
"Als Nutzer:in moechte ich ein oeffentliches Git-Repository per URL importieren koennen, damit ich keine manuellen Uploads brauche." \
"area:backend,prio:could" "M2 - Core Engine"

create_story "E3" "US3.4 - Fehlermeldung bei nicht unterstuetztem Format" \
"Als Nutzer:in moechte ich eine klare Fehlermeldung bekommen, wenn Dateiformat oder -groesse nicht unterstuetzt werden, damit ich weiss, was ich anpassen muss." \
"area:backend,prio:should" "M2 - Core Engine"

# --- E4: Statische Analyse-Engine -----------------------------
create_epic "E4" "E4 - Statische Analyse-Engine" \
"Deterministische, regelbasierte Pruefungen auf Basis eines echten AST ergaenzen den Reflection-Agent." \
"M2 - Core Engine"

create_story "E4" "US4.1 - AST-Erzeugung mit JavaParser" \
"Als Entwickler:in moechte ich Java-Quellcode per JavaParser in einen AST umwandeln, damit strukturelle Pruefungen moeglich sind." \
"area:backend,prio:must" "M2 - Core Engine"

create_story "E4" "US4.2 - Erkennung gaengiger Code-Smells" \
"Als Nutzer:in moechte ich automatisch auf gaengige Code-Smells (lange Methoden, tiefe Verschachtelung, ungenutzte Variablen) hingewiesen werden, damit ich offensichtliche Probleme sofort sehe." \
"area:backend,prio:must" "M2 - Core Engine"

create_story "E4" "US4.3 - Einheitliches Finding-Format" \
"Als Entwickler:in moechte ich die Findings der statischen Analyse in einem einheitlichen Format ausgeben, damit sie mit den LLM-Findings zusammengefuehrt werden koennen." \
"area:backend,prio:must" "M2 - Core Engine"

# --- E5: Reflection-Agent / LLM-Review-Engine -----------------
create_epic "E5" "E5 - Reflection-Agent / LLM-Review-Engine" \
"Das fachliche Herzstueck: ein Generate-Reflect-Refine-Zyklus auf Basis der Claude API mit strukturierter Ausgabe." \
"M2 - Core Engine"

create_story "E5" "US5.1 - Generate: initiales KI-Review" \
"Als Nutzer:in moechte ich ein initiales KI-Review meines Codes erhalten (Generate-Schritt), damit ich schnelles Feedback bekomme." \
"area:llm,prio:must" "M2 - Core Engine"

create_story "E5" "US5.2 - Reflect: Selbstkritik des Agents" \
"Als Nutzer:in moechte ich, dass die KI ihr eigenes Review kritisch prueft (Reflect-Schritt), damit Fehlalarme reduziert werden." \
"area:llm,prio:must" "M2 - Core Engine"

create_story "E5" "US5.3 - Refine: finales, verfeinertes Review" \
"Als Nutzer:in moechte ich ein finales, verfeinertes Review erhalten (Refine-Schritt), damit ich nur relevante, gepruefte Hinweise sehe." \
"area:llm,prio:must" "M2 - Core Engine"

create_story "E5" "US5.4 - Strukturierte Ausgabe per Function-Calling" \
"Als Entwickler:in moechte ich die LLM-Antwort strukturiert (Datei, Zeilen, Kategorie, Schweregrad, Begruendung) ueber Function-Calling erhalten, damit sie automatisiert weiterverarbeitet werden kann." \
"area:llm,prio:must" "M2 - Core Engine"

create_story "E5" "US5.5 - Caching/Begrenzung von LLM-Aufrufen" \
"Als Entwickler:in moechte ich LLM-Aufrufe cachen bzw. begrenzen, damit Kosten und Latenz kontrollierbar bleiben." \
"area:llm,prio:should" "M2 - Core Engine"

# --- E6: Web-Frontend ------------------------------------------
create_epic "E6" "E6 - Web-Frontend" \
"Die Web-Anwendung macht Upload, Reflection-Agent-Ergebnisse und Markierungen fuer Nutzer:innen sichtbar und nutzbar." \
"M3 - Web-Frontend"

create_story "E6" "US6.1 - Upload-UI fuer Projekt/Datei" \
"Als Nutzer:in moechte ich mein Projekt bzw. meine Datei ueber die Web-UI hochladen koennen, damit ich ohne Kommandozeile arbeiten kann." \
"area:frontend,prio:must" "M3 - Web-Frontend"

create_story "E6" "US6.2 - Markierung im Monaco-Editor" \
"Als Nutzer:in moechte ich meinen Code im Monaco-Editor mit farblich markierten Problemzeilen sehen, damit ich Probleme sofort lokalisieren kann." \
"area:frontend,prio:must" "M3 - Web-Frontend"

create_story "E6" "US6.3 - Hover-Details zu Findings" \
"Als Nutzer:in moechte ich beim Hovern ueber eine Markierung Kategorie, Schweregrad, Begruendung und Verbesserungsvorschlag sehen, damit ich direkt handeln kann." \
"area:frontend,prio:must" "M3 - Web-Frontend"

create_story "E6" "US6.4 - Filterbare Findings-Liste" \
"Als Nutzer:in moechte ich alle Findings in einer filterbaren Liste sehen (nach Schweregrad/Kategorie), damit ich Prioritaeten setzen kann." \
"area:frontend,prio:should" "M3 - Web-Frontend"

create_story "E6" "US6.5 - Review-Historie" \
"Als Nutzer:in moechte ich fruehere Reviews meines Projekts einsehen koennen, damit ich Fortschritt nachvollziehen kann." \
"area:frontend,prio:could" "M3 - Web-Frontend"

# --- E7: VS-Code-Plugin ------------------------------------------
create_epic "E7" "E7 - VS-Code-Plugin" \
"Ein duenner Client, der den bestehenden Backend-Kern direkt im Editor nutzbar macht." \
"M4 - Plugin & Politur"

create_story "E7" "US7.1 - Review per Command Palette starten" \
"Als Entwickler:in moechte ich per Command Palette 'Review starten' aus VS Code ausloesen koennen, damit ich meinen Editor nicht verlassen muss." \
"area:vscode,prio:must" "M4 - Plugin & Politur"

create_story "E7" "US7.2 - Findings als native Diagnostics" \
"Als Entwickler:in moechte ich Findings als native Diagnostics (Squiggly Lines) im Editor sehen, damit sie sich wie gewohnte Fehler-/Warnhinweise verhalten." \
"area:vscode,prio:must" "M4 - Plugin & Politur"

create_story "E7" "US7.3 - Hover mit Begruendung/Vorschlag" \
"Als Entwickler:in moechte ich per Hover die Begruendung und den Vorschlag sehen, damit ich ohne Kontextwechsel weiterarbeiten kann." \
"area:vscode,prio:should" "M4 - Plugin & Politur"

create_story "E7" "US7.4 - Quick-Fix fuer Vorschlaege (Stretch)" \
"Als Entwickler:in moechte ich einen Vorschlag per Quick-Fix direkt uebernehmen koennen, damit Korrekturen schneller gehen." \
"area:vscode,prio:could" "M4 - Plugin & Politur"

# --- E8: Qualitaetssicherung & Deployment ------------------------
create_epic "E8" "E8 - Qualitaetssicherung & Deployment" \
"Tests, Deployment und Dokumentation stellen sicher, dass das Projekt vorfuehrbar und nachvollziehbar ist." \
"M4 - Plugin & Politur"

create_story "E8" "US8.1 - Automatisierte Backend-Tests" \
"Als Team moechten wir automatisierte Backend-Tests (JUnit/Mockito) fuer die Kernlogik haben, damit Regressionen frueh auffallen." \
"area:infra,prio:must" "M4 - Plugin & Politur"

create_story "E8" "US8.2 - Deployment per Docker Compose" \
"Als Team moechten wir die Anwendung per Docker Compose lokal und fuer die Demo deployen koennen, damit die Praesentation reibungslos laeuft." \
"area:infra,prio:must" "M4 - Plugin & Politur"

create_story "E8" "US8.3 - README mit Architektur & Setup" \
"Als Team moechten wir eine README mit Architekturueberblick und Setup-Anleitung haben, damit neue Mitglieder oder Dozent:innen das Projekt verstehen." \
"area:infra,prio:should" "M4 - Plugin & Politur"

create_story "E8" "US8.4 - Vorbereitung Abschlusspraesentation" \
"Als Team moechten wir die Abschlusspraesentation anhand eines Beispielprojekts vorbereiten, damit die Kernfeatures ueberzeugend gezeigt werden." \
"area:infra,prio:must" "M4 - Plugin & Politur"

echo ""
echo "Fertig! 8 Epics und 32 User Stories wurden in $REPO angelegt (inkl. Labels & Meilensteine)."
echo "Ansehen unter: https://github.com/$REPO/issues"
