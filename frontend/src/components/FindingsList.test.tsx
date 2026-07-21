import { describe, it, expect } from "vitest";
import "@testing-library/jest-dom/vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FindingsList } from "./FindingsList";
import type { Finding } from "../types/api";

let nextId = 1;

function makeFinding(overrides: Partial<Finding> = {}): Finding {
  return {
    id: nextId++,
    reviewRunId: 1,
    filePath: "src/Main.java",
    lineNumber: 10,
    category: "LONG_METHOD",
    severity: "WARNING",
    description: "Method is too long",
    suggestion: "Split into smaller methods",
    ...overrides,
  };
}

const multiSeverityFindings: Finding[] = [
  makeFinding({ id: 1, severity: "CRITICAL", category: "LONG_METHOD", filePath: "src/A.java", lineNumber: 5 }),
  makeFinding({ id: 2, severity: "WARNING", category: "DEEP_NESTING", filePath: "src/A.java", lineNumber: 20 }),
  makeFinding({ id: 3, severity: "INFO", category: "UNUSED_VARIABLE", filePath: "src/A.java", lineNumber: 30 }),
  makeFinding({ id: 4, severity: "CRITICAL", category: "DEEP_NESTING", filePath: "src/B.java", lineNumber: 10 }),
  makeFinding({ id: 5, severity: "WARNING", category: "LONG_METHOD", filePath: "src/B.java", lineNumber: 15 }),
  makeFinding({ id: 6, severity: "INFO", category: "UNUSED_VARIABLE", filePath: "src/B.java", lineNumber: 40 }),
  makeFinding({ id: 7, severity: "WARNING", category: "LONG_METHOD", filePath: "src/C.java", lineNumber: 8 }),
];

const singleCategoryFindings: Finding[] = [
  makeFinding({ id: 10, category: "LONG_METHOD", filePath: "src/X.java", lineNumber: 1 }),
  makeFinding({ id: 11, category: "LONG_METHOD", filePath: "src/X.java", lineNumber: 2 }),
];

function severityBtn(name: RegExp) {
  const filterBar = screen.getByText("Severity:").closest("div")!;
  return within(filterBar).getByRole("button", { name });
}

function categoryBtn(name: RegExp) {
  const filterBar = screen.getByText("Category:").closest("div")!;
  return within(filterBar).getByRole("button", { name });
}

describe("FindingsList", () => {
  describe("Rendering", () => {
    it("renders findings count badge with total number", () => {
      nextId = 100;
      render(<FindingsList findings={multiSeverityFindings} />);
      expect(screen.getByText("7")).toBeInTheDocument();
    });

    it("renders empty state when findings array is empty", () => {
      render(<FindingsList findings={[]} />);
      expect(screen.getByText("No issues found. Code looks clean!")).toBeInTheDocument();
    });

    it("groups findings under file path headers by default", () => {
      render(<FindingsList findings={multiSeverityFindings} />);
      expect(screen.getByText("src/A.java")).toBeInTheDocument();
      expect(screen.getByText("src/B.java")).toBeInTheDocument();
      expect(screen.getByText("src/C.java")).toBeInTheDocument();
    });
  });

  describe("Severity filtering", () => {
    it("starts with all severities active", () => {
      render(<FindingsList findings={multiSeverityFindings} />);

      const criticalBtn = severityBtn(/critical/i);
      const warningBtn = severityBtn(/warning/i);
      const infoBtn = severityBtn(/info/i);

      expect(criticalBtn).not.toHaveClass("findings-list-filter-btn--inactive");
      expect(warningBtn).not.toHaveClass("findings-list-filter-btn--inactive");
      expect(infoBtn).not.toHaveClass("findings-list-filter-btn--inactive");

      expect(criticalBtn).toHaveTextContent("2 Critical");
      expect(warningBtn).toHaveTextContent("3 Warning");
      expect(infoBtn).toHaveTextContent("2 Info");
    });

    it("toggles a severity OFF and hides matching findings", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(severityBtn(/critical/i));

      expect(screen.getByText("5 / 7")).toBeInTheDocument();
      expect(severityBtn(/critical/i)).toHaveClass("findings-list-filter-btn--inactive");
    });

    it("toggles severity back ON and restores findings", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      const btn = severityBtn(/critical/i);
      await user.click(btn);
      await user.click(btn);

      expect(screen.getByText("src/A.java")).toBeInTheDocument();
      expect(screen.queryByText(" / ")).not.toBeInTheDocument();
    });

    it("prevents deselecting the last remaining severity", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(severityBtn(/warning/i));
      await user.click(severityBtn(/info/i));

      // Only CRITICAL remains active
      expect(severityBtn(/critical/i)).not.toHaveClass("findings-list-filter-btn--inactive");
      expect(severityBtn(/warning/i)).toHaveClass("findings-list-filter-btn--inactive");
      expect(severityBtn(/info/i)).toHaveClass("findings-list-filter-btn--inactive");

      // Can't deselect the last one
      await user.click(severityBtn(/critical/i));

      expect(severityBtn(/critical/i)).not.toHaveClass("findings-list-filter-btn--inactive");
    });

    it("shows 'X / Y' count when filters are active", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      expect(screen.queryByText(" / ")).not.toBeInTheDocument();

      await user.click(severityBtn(/info/i));

      expect(screen.getByText("5 / 7")).toBeInTheDocument();
    });
  });

  describe("Category filtering", () => {
    it("hides category filter row when only 1 category exists", () => {
      render(<FindingsList findings={singleCategoryFindings} />);
      expect(screen.queryByText("Category:")).not.toBeInTheDocument();
    });

    it("shows category filter row when multiple categories exist", () => {
      render(<FindingsList findings={multiSeverityFindings} />);
      expect(screen.getByText("Category:")).toBeInTheDocument();
    });

    it("toggles a category OFF and hides matching findings", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      const btn = categoryBtn(/long_method/i);
      expect(btn).toHaveTextContent("3 LONG_METHOD");

      await user.click(btn);

      expect(screen.getByText("4 / 7")).toBeInTheDocument();
    });

    it("prevents deselecting the last remaining category", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(categoryBtn(/deep_nesting/i));
      await user.click(categoryBtn(/unused_variable/i));

      const btn = categoryBtn(/long_method/i);
      expect(btn).not.toHaveClass("findings-list-filter-btn--inactive");

      await user.click(btn);

      expect(btn).not.toHaveClass("findings-list-filter-btn--inactive");
    });
  });

  describe("Grouping", () => {
    it("defaults to grouping by file", () => {
      render(<FindingsList findings={multiSeverityFindings} />);
      expect(screen.getByText("src/A.java")).toBeInTheDocument();
      expect(screen.getByRole("radio", { name: /file/i })).toBeChecked();
    });

    it("switches to group-by-severity mode", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(screen.getByRole("radio", { name: /severity/i }));

      const groups = screen.getAllByRole("button", { name: /findings?$/i });
      expect(groups.some((g) => g.textContent?.includes("Critical"))).toBe(true);
      expect(groups.some((g) => g.textContent?.includes("Warning"))).toBe(true);
      expect(groups.some((g) => g.textContent?.includes("Info"))).toBe(true);
    });

    it("switches to group-by-category mode", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(screen.getByRole("radio", { name: /category/i }));

      const groups = screen.getAllByRole("button", { name: /findings?$/i });
      expect(groups.some((g) => g.textContent?.includes("LONG_METHOD"))).toBe(true);
      expect(groups.some((g) => g.textContent?.includes("DEEP_NESTING"))).toBe(true);
      expect(groups.some((g) => g.textContent?.includes("UNUSED_VARIABLE"))).toBe(true);
    });
  });

  describe("Reset filters", () => {
    it("shows 'Reset filters' button when any filter is active", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      expect(screen.queryByRole("button", { name: /reset filters/i })).not.toBeInTheDocument();

      await user.click(severityBtn(/info/i));

      expect(screen.getByRole("button", { name: /reset filters/i })).toBeInTheDocument();
    });

    it("resets all severity and category filters on click", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(severityBtn(/critical/i));
      await user.click(categoryBtn(/long_method/i));
      expect(screen.getByText("3 / 7")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /reset filters/i }));

      expect(screen.getByText("7")).toBeInTheDocument();
    });

    it("hides reset button after filters are cleared", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(severityBtn(/info/i));
      expect(screen.getByRole("button", { name: /reset filters/i })).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /reset filters/i }));
      expect(screen.queryByRole("button", { name: /reset filters/i })).not.toBeInTheDocument();
    });
  });

  describe("Collapsible sections", () => {
    it("collapses a group section on header click", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      const groupHeader = screen.getAllByText("src/A.java")[0].closest("button")!;
      expect(groupHeader).toHaveAttribute("aria-expanded", "true");

      await user.click(groupHeader);

      expect(groupHeader).toHaveAttribute("aria-expanded", "false");
      expect(screen.queryByText("Method is too long")).not.toBeInTheDocument();
    });

    it("expands a finding card to show description and suggestion", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      const cards = document.querySelectorAll(".findings-card");
      const firstCard = cards[0] as HTMLElement;
      expect(firstCard).toHaveAttribute("aria-expanded", "false");

      await user.click(firstCard);

      expect(firstCard).toHaveAttribute("aria-expanded", "true");
      expect(screen.getByText("Method is too long")).toBeInTheDocument();
      expect(screen.getByText("Split into smaller methods")).toBeInTheDocument();
    });
  });

  describe("Sorting", () => {
    it("sorts findings within a group by line number then severity", () => {
      const findings = [
        makeFinding({ id: 101, filePath: "src/Same.java", lineNumber: 30, severity: "CRITICAL" }),
        makeFinding({ id: 102, filePath: "src/Same.java", lineNumber: 10, severity: "INFO" }),
        makeFinding({ id: 103, filePath: "src/Same.java", lineNumber: 10, severity: "WARNING" }),
      ];

      render(<FindingsList findings={findings} />);

      const groupItems = document.querySelectorAll(".findings-file-items .findings-card");

      expect(groupItems[0]).toHaveTextContent("line 10");
      expect(groupItems[0]).toHaveTextContent("WARNING");
      expect(groupItems[1]).toHaveTextContent("line 10");
      expect(groupItems[1]).toHaveTextContent("INFO");
      expect(groupItems[2]).toHaveTextContent("line 30");
      expect(groupItems[2]).toHaveTextContent("CRITICAL");
    });
  });

  describe("Empty filter state", () => {
    it("shows 'No findings match' with reset link when all filtered out", async () => {
      const user = userEvent.setup();
      render(<FindingsList findings={multiSeverityFindings} />);

      await user.click(severityBtn(/critical/i));
      await user.click(severityBtn(/warning/i));
      // Can't deselect the last severity (INFO), so also deselect all categories
      await user.click(categoryBtn(/deep_nesting/i));
      await user.click(categoryBtn(/unused_variable/i));

      expect(screen.getByText(/No findings match the current filters/)).toBeInTheDocument();
      expect(screen.getAllByRole("button", { name: /reset filters/i }).length).toBeGreaterThanOrEqual(1);
    });
  });
});
