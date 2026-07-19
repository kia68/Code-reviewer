import * as path from "path";
import * as vscode from "vscode";
import { CodeReviewerApiClient, CodeReviewerApiError, Finding } from "./apiClient";
import {
  buildHoverMarkdown,
  clampLineIndex,
  DiagnosticSeverityLevel,
  findingsAtLine,
  severityLevel,
} from "./diagnostics";

let diagnosticCollection: vscode.DiagnosticCollection;
const findingsByDocument = new Map<string, Finding[]>();

export function activate(context: vscode.ExtensionContext): void {
  diagnosticCollection = vscode.languages.createDiagnosticCollection("codeReviewer");
  context.subscriptions.push(diagnosticCollection);
  context.subscriptions.push(
    vscode.workspace.onDidCloseTextDocument((document) => {
      diagnosticCollection.delete(document.uri);
      findingsByDocument.delete(document.uri.toString());
    }),
  );

  context.subscriptions.push(
    vscode.languages.registerHoverProvider({ scheme: "file", pattern: "**/*.java" }, { provideHover }),
  );

  const disposable = vscode.commands.registerCommand("codeReviewer.startReview", startReview);
  context.subscriptions.push(disposable);
}

export function deactivate(): void {}

async function startReview(): Promise<void> {
  const editor = vscode.window.activeTextEditor;
  if (!editor) {
    vscode.window.showWarningMessage("Kein aktiver Editor. Bitte eine Java-Datei öffnen.");
    return;
  }

  const document = editor.document;
  if (path.extname(document.fileName).toLowerCase() !== ".java") {
    vscode.window.showWarningMessage("Code Reviewer unterstützt aktuell nur .java-Dateien.");
    return;
  }

  const baseUrl = vscode.workspace
    .getConfiguration("codeReviewer")
    .get<string>("apiBaseUrl", "http://localhost:8080");
  const client = new CodeReviewerApiClient(baseUrl);
  const projectName = vscode.workspace.name ?? "VS Code Workspace";
  const fileName = path.basename(document.fileName);
  const content = document.getText();

  await vscode.window.withProgress(
    { location: vscode.ProgressLocation.Notification, title: "Code Reviewer", cancellable: false },
    async (progress) => {
      try {
        progress.report({ message: "Projekt wird ermittelt…" });
        const project = await client.findOrCreateProject(projectName);

        progress.report({ message: `${fileName} wird hochgeladen…` });
        const reviewRun = await client.createReviewRun(project.id, fileName, content);

        progress.report({ message: "Code wird analysiert…" });
        const findings = await client.analyzeFindings(project.id, reviewRun.id);

        diagnosticCollection.set(document.uri, toDiagnostics(document, findings));
        findingsByDocument.set(document.uri.toString(), findings);

        vscode.window.showInformationMessage(
          findings.length === 0
            ? `Review abgeschlossen: keine Findings in ${fileName}.`
            : `Review abgeschlossen: ${findings.length} Finding(s) in ${fileName}.`,
        );
      } catch (error) {
        const message = error instanceof CodeReviewerApiError ? error.message : String(error);
        vscode.window.showErrorMessage(`Code Reviewer: ${message}`);
      }
    },
  );
}

function toDiagnostics(document: vscode.TextDocument, findings: Finding[]): vscode.Diagnostic[] {
  return findings.map((finding) => {
    const lineIndex = clampLineIndex(finding.lineNumber, document.lineCount);
    const range = document.lineAt(lineIndex).range;

    const diagnostic = new vscode.Diagnostic(range, finding.description, toVsCodeSeverity(severityLevel(finding.severity)));
    diagnostic.source = "Code Reviewer";
    diagnostic.code = finding.category;
    return diagnostic;
  });
}

function provideHover(document: vscode.TextDocument, position: vscode.Position): vscode.Hover | undefined {
  const findings = findingsByDocument.get(document.uri.toString());
  if (!findings || findings.length === 0) {
    return undefined;
  }

  const atLine = findingsAtLine(findings, position.line, document.lineCount);
  if (atLine.length === 0) {
    return undefined;
  }

  const markdown = new vscode.MarkdownString(buildHoverMarkdown(atLine));
  markdown.isTrusted = false;
  return new vscode.Hover(markdown, document.lineAt(position.line).range);
}

function toVsCodeSeverity(level: DiagnosticSeverityLevel): vscode.DiagnosticSeverity {
  switch (level) {
    case "error":
      return vscode.DiagnosticSeverity.Error;
    case "warning":
      return vscode.DiagnosticSeverity.Warning;
    case "information":
      return vscode.DiagnosticSeverity.Information;
  }
}