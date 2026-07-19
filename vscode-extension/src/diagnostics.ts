import { Severity } from "./apiClient";

export type DiagnosticSeverityLevel = "error" | "warning" | "information";

export function severityLevel(severity: Severity): DiagnosticSeverityLevel {
  switch (severity) {
    case "CRITICAL":
      return "error";
    case "WARNING":
      return "warning";
    case "INFO":
      return "information";
    default:
      return "information";
  }
}

// Findings carry a 1-based line number from the backend that may be stale relative to the
// current document (e.g. re-analyzed after edits); clamp it into a valid 0-based line index
// instead of letting document.lineAt() throw on an out-of-range value.
export function clampLineIndex(lineNumber: number, lineCount: number): number {
  if (lineCount <= 0) {
    return 0;
  }
  const zeroBased = lineNumber - 1;
  return Math.min(Math.max(zeroBased, 0), lineCount - 1);
}
