import { describe, expect, it } from "vitest";
import { clampLineIndex, severityLevel } from "../src/diagnostics";
import { Severity } from "../src/apiClient";

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
