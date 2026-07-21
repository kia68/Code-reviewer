# Demo-Ablauf für die Abschlusspräsentation

Ablaufplan für eine Live-Vorführung (ca. 8–10 Minuten). Das Beispielprojekt unter `demo/src/`
enthält bewusst platzierte Probleme, sodass jede Kernfähigkeit genau einmal sichtbar wird.

---

## Vorbereitung (vor der Präsentation, nicht live)

```bash
# 1. Kompletten Stack starten
docker compose up --build -d

# 2. Warten bis das Backend bereit ist (sollte {"status":"UP"} liefern)
curl http://localhost:8080/actuator/health

# 3. VS-Code-Plugin kompilieren
cd vscode-extension && npm install && npm run compile && cd ..
```

Der erste Docker-Build dauert einige Minuten – **unbedingt vorher erledigen**, nicht live.

---

## Das Beispielprojekt

`demo/src/` enthält drei Java-Dateien eines fiktiven Shop-Systems:

| Datei | Eingebautes Problem | Erwartetes Finding |
|---|---|---|
| `OrderProcessor.java` | 4-fach verschachtelte Rabattlogik | `DEEP_NESTING` (Zeile 10, WARNING) |
| `OrderProcessor.java` | ungenutzte Variable `unusedBaseRate` | `UNUSED_VARIABLE` (Zeile 5, INFO) |
| `InvoiceReport.java` | 46-zeilige Methode `printInvoice` | `LONG_METHOD` (Zeile 6, WARNING) |
| `CustomerRegistry.java` | ungenutzte Variable `maxRetries` | `UNUSED_VARIABLE` (Zeile 10, INFO) |

Insgesamt **4 Findings über 3 Dateien**, in allen drei Kategorien und allen Schweregraden,
die die statische Analyse aktuell erkennt.

---

## Teil 1 – Das Problem (ca. 1 Min)

`demo/src/OrderProcessor.java` im Editor zeigen und die verschachtelte `calculateDiscount`-Methode
kurz erklären: technisch korrekt, aber schwer lesbar und wartbar. Genau die Art Problem, die in
einem manuellen Review oft übersehen wird oder erst spät auffällt.

---

## Teil 2 – Review über die Web-Oberfläche (ca. 3 Min)

1. `http://localhost:5173` öffnen.
2. Projekt anlegen, z. B. „Shop-System".
3. `demo/demo-project.zip` hochladen (bzw. neu erzeugen: im Ordner `demo/src` alle `.java`-Dateien zippen).
4. Analyse starten.
5. Ergebnis zeigen: alle 4 Findings, farblich markierte Zeilen im Editor, Hover mit Begründung
   und Verbesserungsvorschlag.

**Kernaussage:** Kein Setup im Projekt nötig, keine Konfiguration – Code rein, Befunde raus.

---

## Teil 3 – Review direkt in VS Code (ca. 3 Min)

```bash
code --extensionDevelopmentPath="vscode-extension" "demo/src/OrderProcessor.java"
```

Im neu geöffneten Fenster (lila Titelleiste = Extension-Development-Host):

1. `Strg+Shift+P` → **„Code Reviewer: Review starten"**.
2. **Squiggly Lines** erscheinen direkt im Code – wie gewohnte Compiler-Warnungen.
3. **Problems-Panel** (`Strg+Shift+M`) öffnen: Findings wie native Fehler gelistet.
4. **Hover** über Zeile 10 (`DEEP_NESTING`): Kategorie, Schweregrad, Begründung, Vorschlag.
5. **Quick-Fix** auf Zeile 5 (`unusedBaseRate`) mit `Strg+.` → „Ungenutzte Variable entfernen"
   löscht die Zeile direkt.
6. Optional: Quick-Fix auf Zeile 10 zeigt, dass dort **kein** automatischer Umbau passiert,
   sondern der Vorschlag als `// TODO(Code Reviewer): ...` eingefügt wird.

**Kernaussage:** Kein Kontextwechsel – das Review erscheint dort, wo entwickelt wird. Punkt 6 ist
bewusst ehrlich: „Methode aufteilen" lässt sich nicht sicher automatisieren, deshalb wird es auch
nicht so verkauft.

---

## Teil 4 – Architektur in einem Satz (ca. 2 Min)

Architekturdiagramm aus der [README](../README.md#-architektur-im-überblick) zeigen:

* Beide Clients sprechen **dieselbe REST-API**, keine Analyse-Logik im Client.
* Statische Analyse (JavaParser + Detektoren) und LLM-Review schreiben ins **gleiche
  `Finding`-Format** → beide Quellen fließen ohne Sonderfälle zusammen.
* Deshalb erscheinen neue Analyse-Fähigkeiten des Backends automatisch in Web-UI **und** Plugin,
  ohne Client-Änderung.

---

## Fallback, falls live etwas klemmt

* **Backend antwortet nicht:** `docker compose ps` prüfen, `docker compose logs backend` zeigt den Grund.
* **Plugin zeigt nichts an:** Häufigste Ursache ist ein nicht laufendes Backend – die Extension
  meldet den Fehler als Notification. `codeReviewer.apiBaseUrl` prüfen.
* **Keine Findings:** Sicherstellen, dass wirklich eine `.java`-Datei aktiv ist (das Plugin
  verarbeitet nur die aktive Datei) bzw. das ZIP `.java`-Dateien enthält.
* **Notnagel:** Die erwarteten Findings stehen in der Tabelle oben – zur Not lässt sich der
  Ablauf auch anhand dieser Werte erklären.

---

## Was bewusst nicht Teil der Demo ist

Ehrlichkeit an diesen Punkten ist bei Nachfragen besser als Überversprechen:

* **LLM-Review (Generate-Reflect-Refine):** im Backend implementiert, aber noch nicht in `main`
  gemergt – daher zeigt die Demo die deterministische statische Analyse. Weil beide dasselbe
  `Finding`-Format nutzen, erscheinen LLM-Befunde nach dem Merge ohne Client-Änderung.
* **Nur Java:** Andere Sprachen sind nicht implementiert.
* **Nur drei Smell-Kategorien:** lange Methoden, tiefe Verschachtelung, ungenutzte Variablen.
* **Keine Authentifizierung:** Es gibt keine Benutzerverwaltung; das System ist als lokal
  laufendes Werkzeug ausgelegt.
