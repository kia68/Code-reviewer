import { describe, expect, it } from "vitest";
import { buildHoverMarkdown, clampLineIndex, findingsAtLine, hasMechanicalFix, severityLevel } from "../src/diagnostics";
import { Finding, Severity } from "../src/apiClient";

function finding(overrides: Partial<Finding> = {}): Finding {
  return {
    id: 1,
    reviewRunId: 1,
    filePath: "Hello.java",
    lineNumber: 3,
    category: "LONG_METHOD",
    severity: "WARNING",
    description: "Methode ist zu lang.",
    suggestion: "In kleinere Methoden aufteilen.",
    ...overrides,
  };
}

describe("severityLevel", () => {
  it("maps CRITICAL to error", () => {
    expect(severityLevel("CRITICAL")).toBe("error");
  });

  it("maps WARNING to warning", () => {
    expect(severityLevel("WARNING")).toBe("warning");
  });

  it("maps INFO to information", () => {
    expect(severityLevel("INFO")).toBe("information");
  });

  it("defaults unknown severities to information", () => {
    expect(severityLevel("SOMETHING_NEW" as Severity)).toBe("information");
  });
});

describe("clampLineIndex", () => {
  it("converts a 1-based line number to a 0-based index", () => {
    expect(clampLineIndex(5, 10)).toBe(4);
  });

  it("clamps the first line to index 0", () => {
    expect(clampLineIndex(1, 10)).toBe(0);
  });

  it("clamps line numbers below 1 to index 0", () => {
    expect(clampLineIndex(0, 10)).toBe(0);
    expect(clampLineIndex(-5, 10)).toBe(0);
  });

  it("clamps line numbers beyond the document to the last line", () => {
    expect(clampLineIndex(999, 10)).toBe(9);
  });

  it("returns 0 for an empty document", () => {
    expect(clampLineIndex(1, 0)).toBe(0);
  });
});

describe("findingsAtLine", () => {
  it("returns findings whose clamped line matches the hovered line", () => {
    const findings = [finding({ id: 1, lineNumber: 3 }), finding({ id: 2, lineNumber: 7 })];

    expect(findingsAtLine(findings, 2, 10)).toEqual([findings[0]]);
    expect(findingsAtLine(findings, 6, 10)).toEqual([findings[1]]);
  });

  it("returns an empty array when no finding matches the line", () => {
    const findings = [finding({ lineNumber: 3 })];

    expect(findingsAtLine(findings, 9, 10)).toEqual([]);
  });

  it("returns multiple findings that clamp onto the same line", () => {
    const findings = [finding({ id: 1, lineNumber: 999 }), finding({ id: 2, lineNumber: 20 })];

    expect(findingsAtLine(findings, 9, 10)).toHaveLength(2);
  });
});

describe("buildHoverMarkdown", () => {
  it("includes category, severity, description and suggestion", () => {
    const markdown = buildHoverMarkdown([finding()]);

    expect(markdown).toContain("LONG_METHOD");
    expect(markdown).toContain("WARNING");
    expect(markdown).toContain("Methode ist zu lang.");
    expect(markdown).toContain("In kleinere Methoden aufteilen.");
  });

  it("omits the suggestion section when there is no suggestion", () => {
    const markdown = buildHoverMarkdown([finding({ suggestion: null })]);

    expect(markdown).not.toContain("Vorschlag");
  });

  it("joins multiple findings on the same line with a separator", () => {
    const markdown = buildHoverMarkdown([
      finding({ id: 1, category: "LONG_METHOD" }),
      finding({ id: 2, category: "UNUSED_VARIABLE" }),
    ]);

    expect(markdown).toContain("LONG_METHOD");
    expect(markdown).toContain("UNUSED_VARIABLE");
    expect(markdown).toContain("---");
  });
});

describe("hasMechanicalFix", () => {
  it("is true for UNUSED_VARIABLE", () => {
    expect(hasMechanicalFix("UNUSED_VARIABLE")).toBe(true);
  });

  it("is false for categories with no safe automatic edit", () => {
    expect(hasMechanicalFix("LONG_METHOD")).toBe(false);
    expect(hasMechanicalFix("DEEP_NESTING")).toBe(false);
  });

  it("is false for unknown categories", () => {
    expect(hasMechanicalFix("SOME_FUTURE_LLM_CATEGORY")).toBe(false);
  });
});
