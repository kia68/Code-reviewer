# Automatischer Code-Reviewer

Der **Automatische Code-Reviewer** ist ein intelligentes Review-Werkzeug, das hochgeladenen Java-Code sowohl über eine deterministische statische Analyse (AST-basiert via JavaParser) als auch über ein fortschrittliches LLM-gestütztes Review (Claude API mit dem "Generate-Reflect-Refine"-Ansatz) bewertet. Die Ergebnisse (Code-Smells, logische Fehler, Verbesserungsvorschläge) werden interaktiv im Web-Frontend (Monaco Editor mit Markierungen) sowie direkt in der VS-Code-Entwicklungsumgebung angezeigt.

---

## 🛠️ Tech-Stack

### Backend
* **Sprache & Laufzeit:** Java 21 (OpenJDK Temurin)
* **Framework:** Spring Boot 3.3.x
* **Build-Tool:** Gradle 9 (Kotlin DSL)
* **Datenbank & Persistenz:** PostgreSQL 16, Spring Data JPA, Flyway für Migrationen
* **Code-Analyse:** JavaParser (für AST-basierte statische Smells)
* **LLM-Integration:** Anthropic Claude API (per HttpClient / RestClient)

### Frontend
* **Framework:** React 18+ mit Vite & TypeScript
* **Editor:** Monaco Editor (für native Code-Hervorhebung und Finding-Markierungen)
* **Styling:** Vanilla CSS (modernes UI-Design mit Dark Mode und harmonischen Paletten)

### VS-Code-Plugin
* **Laufzeit:** Node.js, VS Code Extension API
* **Funktion:** Triggerung von Reviews und Visualisierung über native VS-Code-Diagnostics (Squiggly Lines) und Hovers.

---

## 📁 Repository-Struktur (Monorepo)

```text
code-reviewer/
├── .github/
│   ├── workflows/           # CI/CD Pipelines (GitHub Actions)
│   └── pull_request_template.md
├── backend/                 # Spring Boot Backend (Gradle)
│   ├── config/              # Checkstyle-Konfigurationen
│   ├── gradle/wrapper/      # Gradle Wrapper-Dateien
│   ├── src/                 # Modularer Java-Quellcode & Migrationen
│   ├── build.gradle.kts     # Gradle-Buildskript
│   ├── settings.gradle.kts  # Gradle-Settings
│   ├── gradlew / gradlew.bat # Gradle Wrapper-Ausführungsdateien
│   └── Dockerfile           # Multi-Stage-Build (Gradle -> JRE-Image)
├── frontend/                # React Web-App (Vite)
│   ├── src/                 # React Komponenten & Assets
│   ├── .prettierrc          # Prettier Formatierungsregeln
│   ├── package.json         # NPM Scripts & Dependencies
│   └── Dockerfile           # Multi-Stage-Build (Vite-Build -> nginx)
├── docker-compose.yml       # Postgres + Backend + Frontend als Container
└── README.md                # Projektdokumentation
```


### ☕ Backend-Architektur & Paketstruktur
Das Backend ist als **Modularer Monolith** (Package-by-Feature) aufgebaut, um eine saubere Kapselung der Komponenten und konfliktfreie parallele Entwicklung im Team zu gewährleisten:
* `de.ude.codereviewer.project`: Verwaltet die Projekte und REST-APIs für das Projektmanagement.
* `de.ude.codereviewer.review`: Verwaltet die Review-Läufe (`ReviewRun`) und Analysebefunde (`Finding`).
* `de.ude.codereviewer.ingestion`: Verwaltet den Import und die Validierung von hochgeladenem Code (Dateien/ZIPs).
* `de.ude.codereviewer.analysis`: Enthält die Schnittstellen und Implementierungen der Analyse-Engines (AST Parser & LLM Reflection Agent).

---


## 🚀 Erste Schritte / Lokale Entwicklung

### Voraussetzungen
Stelle sicher, dass folgende Software auf deinem Rechner installiert ist:
* **Java 21** (z. B. Eclipse Temurin)
* **Node.js** (v18 oder neuer) & `npm`
* **Docker** & **Docker Compose**
* **GitHub CLI (gh)** (falls du das Backlog importieren möchtest)

---

### Schnellstart: alles über Docker Compose

Am schnellsten testest du Backend + Frontend zusammen mit einem einzigen Befehl im Projekt-Root
(keine lokale Java-/Node-Installation nötig, nur Docker):

```bash
docker compose up --build
```

Das startet drei Container:
* **PostgreSQL** – Port `5432` (Datenbank `codereviewer`, User `postgres`, Passwort `password`)
* **Backend** (Spring Boot) – `http://localhost:8080`
* **Frontend** (React, per nginx ausgeliefert) – `http://localhost:5173`

Zum Beenden: `Strg+C`, danach `docker compose down` (Datenbank-Inhalt bleibt im Volume `pgdata` erhalten,
hochgeladener Code im Volume `backend-uploads`; `docker compose down -v` löscht auch diese Volumes).

*Hinweis:* Das erste `--build` lädt Basis-Images herunter und baut Backend (Gradle) sowie Frontend
(Vite) – je nach Internetverbindung kann das ein paar Minuten dauern. Spätere Starts (ohne Code-Änderung)
gehen mit `docker compose up` (ohne `--build`) deutlich schneller.

Wenn du stattdessen mit Hot-Reload lokal entwickeln willst (Code-Änderungen sofort sehen, ohne Neubau),
nutze die Schritte 1–3 unten statt Docker Compose.

---

### 1. Datenbank starten
Die PostgreSQL-Datenbank wird vorkonfiguriert über Docker Compose bereitgestellt:

```bash
docker compose up -d postgres
```
*Die Datenbank läuft auf Port `5432` mit der Datenbank `codereviewer` (User: `postgres`, Passwort: `password`).*

---

### 2. Backend starten
Navigiere in den Backend-Ordner und starte die Spring-Boot-Anwendung:

```bash
cd backend
# Falls kein lokales Gradle installiert ist, nutze den Wrapper (wird beim ersten Setup generiert):
./gradlew bootRun
```
*Das Backend läuft unter `http://localhost:8080`.*

---

### 3. Frontend starten
Navigiere in den Frontend-Ordner, installiere die Abhängigkeiten und starte den Entwicklungsserver:

```bash
cd frontend
npm install
npm run dev
```
*Das Frontend läuft unter `http://localhost:5173`.*

---

## 🗺️ Meilensteine (Roadmap)

* 🏁 **M1 - Fundament (Woche 1):** Repository-Setup, Spring-Boot- und React-Skeleton, Datenbank-Anbindung und erste REST-APIs.
* 🧠 **M2 - Core Engine (Woche 2):** Reflection-Agent (Generate-Reflect-Refine-Zyklus), AST-Analyse (JavaParser) und Persistenz der Ergebnisse.
* 🖥️ **M3 - Web-Frontend (Woche 3):** Upload-Schnittstelle, Monaco Editor-Integration mit farbiger Markierung und Hover-Details der Findings.
* 🔌 **M4 - Plugin & Politur (Woche 4):** VS-Code-Plugin-Integration, automatisierte Tests, Dokumentation und Vorbereitung der Abschlusspräsentation.
