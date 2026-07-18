import * as path from "path";
import * as vscode from "vscode";
import { CodeReviewerApiClient, CodeReviewerApiError } from "./apiClient";

export function activate(context: vscode.ExtensionContext): void {
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