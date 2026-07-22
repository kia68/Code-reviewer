import * as path from "path";
import * as vscode from "vscode";
import { CodeReviewerApiClient, CodeReviewerApiError, Finding } from "./apiClient";
import {
  buildHoverMarkdown,
  clampLineIndex,
  DiagnosticSeverityLevel,
  findingsAtLine,
  hasMechanicalFix,
  severityLevel,
} from "./diagnostics";

const DIAGNOSTIC_SOURCE = "Code Reviewer";

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

  context.subscriptions.push(
    vscode.languages.registerCodeActionsProvider(
      { scheme: "file", pattern: "**/*.java" },
      { provideCodeActions },
      { providedCodeActionKinds: [vscode.CodeActionKind.QuickFix] },
    ),
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
    // Surface the origin (KI vs static analysis) in the Problems-panel source column.
    const origin = finding.source === "LLM" ? "KI" : finding.source === "AST" ? "AST" : "";
    diagnostic.source = origin ? `${DIAGNOSTIC_SOURCE} · ${origin}` : DIAGNOSTIC_SOURCE;
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

function provideCodeActions(
  document: vscode.TextDocument,
  _range: vscode.Range | vscode.Selection,
  context: vscode.CodeActionContext,
): vscode.CodeAction[] {
  const findings = findingsByDocument.get(document.uri.toString());
  if (!findings || findings.length === 0) {
    return [];
  }

  const actions: vscode.CodeAction[] = [];
  for (const diagnostic of context.diagnostics) {
    if (!diagnostic.source || !diagnostic.source.startsWith(DIAGNOSTIC_SOURCE)) {
      continue;
    }
    const lineIndex = diagnostic.range.start.line;
    for (const finding of findingsAtLine(findings, lineIndex, document.lineCount)) {
      if (hasMechanicalFix(finding.category)) {
        actions.push(buildRemoveLineAction(document, diagnostic, lineIndex));
      }
      if (finding.suggestion) {
        actions.push(buildCommentSuggestionAction(document, diagnostic, finding.suggestion));
      }
    }
  }
  return actions;
}

// The one category where the suggestion ("Ungenutzte Variable entfernen") maps to an
// unambiguous edit: delete the declaration line outright, including its line break.
function buildRemoveLineAction(
  document: vscode.TextDocument,
  diagnostic: vscode.Diagnostic,
  lineIndex: number,
): vscode.CodeAction {
  const action = new vscode.CodeAction("Ungenutzte Variable entfernen", vscode.CodeActionKind.QuickFix);
  action.diagnostics = [diagnostic];
  action.isPreferred = true;
  const edit = new vscode.WorkspaceEdit();
  edit.delete(document.uri, document.lineAt(lineIndex).rangeIncludingLineBreak);
  action.edit = edit;
  return action;
}

// Fallback for every other category: the suggestion is prose advice (e.g. "split this
// method up"), not something we can safely rewrite for the user - so this inserts it as a
// TODO comment right above the flagged line instead of guessing at a code transformation.
function buildCommentSuggestionAction(
  document: vscode.TextDocument,
  diagnostic: vscode.Diagnostic,
  suggestion: string,
): vscode.CodeAction {
  const action = new vscode.CodeAction(`Vorschlag als TODO übernehmen: ${suggestion}`, vscode.CodeActionKind.QuickFix);
  action.diagnostics = [diagnostic];
  const line = document.lineAt(diagnostic.range.start.line);
  const indent = line.text.match(/^\s*/)?.[0] ?? "";
  const edit = new vscode.WorkspaceEdit();
  edit.insert(document.uri, line.range.start, `${indent}// TODO(Code Reviewer): ${suggestion}\n`);
  action.edit = edit;
  return action;
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