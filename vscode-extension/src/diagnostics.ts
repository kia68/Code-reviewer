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

// Categories where "apply the suggestion" is a safe, unambiguous text edit rather than a
// judgment call (e.g. LONG_METHOD's suggestion is "split this up", which no mechanical edit
// can do correctly). Backend only reports a line number, not a column range, so the fix is
// line-granular - only correct when the declaration is the only thing on its line, which is
// the common case for this detector's output.
const MECHANICALLY_FIXABLE_CATEGORIES = new Set(["UNUSED_VARIABLE"]);

export function hasMechanicalFix(category: string): boolean {
  return MECHANICALLY_FIXABLE_CATEGORIES.has(category);
}

// Human-readable origin badge for a finding. "LLM" -> KI (the AI review),
// "AST" -> static analysis. Unknown/missing source yields an empty label so
// older findings degrade gracefully.
export function sourceLabel(source: string | null | undefined): string {
  switch (source) {
    case "LLM":
      return "🤖 KI";
    case "AST":
      return "📐 AST";
    default:
      return "";
  }
}

// Renders one or more findings on the same line as Markdown for the hover popup:
// category + severity + origin (KI/AST) as a heading, the description as the reasoning,
// and the suggestion (if any) called out separately as an actionable next step.
export function buildHoverMarkdown(findings: Finding[]): string {
  return findings
    .map((finding) => {
      const badge = sourceLabel(finding.source);
      const heading = badge
        ? `**${finding.category}** _(${finding.severity})_ · ${badge}`
        : `**${finding.category}** _(${finding.severity})_`;
      const sections = [heading, "", finding.description];
      if (finding.suggestion) {
        sections.push("", `**Vorschlag:** ${finding.suggestion}`);
      }
      return sections.join("\n");
    })
    .join("\n\n---\n\n");
}
