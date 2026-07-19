import { Finding, Severity } from "./apiClient";

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

// Findings whose (clamped) line matches the hovered line, in the order they were returned.
export function findingsAtLine(findings: Finding[], lineIndex: number, lineCount: number): Finding[] {
  return findings.filter((finding) => clampLineIndex(finding.lineNumber, lineCount) === lineIndex);
}

// Renders one or more findings on the same line as Markdown for the hover popup:
// category + severity as a heading, the description as the reasoning, and the suggestion
// (if any) called out separately so it reads as an actionable next step, not more prose.
export function buildHoverMarkdown(findings: Finding[]): string {
  return findings
    .map((finding) => {
      const sections = [`**${finding.category}** _(${finding.severity})_`, "", finding.description];
      if (finding.suggestion) {
        sections.push("", `**Vorschlag:** ${finding.suggestion}`);
      }
      return sections.join("\n");
    })
    .join("\n\n---\n\n");
}
