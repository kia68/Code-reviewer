import { useMemo, useState } from "react";
import type { Finding, Severity } from "../types/api";
import "./FindingsList.css";

interface FindingsListProps {
  findings: Finding[];
}

const ALL_SEVERITIES: Severity[] = ["CRITICAL", "WARNING", "INFO"];
type GroupBy = "file" | "severity" | "category";

interface GroupedFindings {
  label: string;
  findings: Finding[];
}

function sortFindings(list: Finding[]): Finding[] {
  const severityOrder: Record<Severity, number> = { CRITICAL: 0, WARNING: 1, INFO: 2 };
  return [...list].sort(
    (a, b) =>
      (a.lineNumber ?? Infinity) - (b.lineNumber ?? Infinity) ||
      severityOrder[a.severity] - severityOrder[b.severity],
  );
}

function countBy<T>(items: Finding[], key: (f: Finding) => T): Map<T, number> {
  const map = new Map<T, number>();
  for (const item of items) {
    const k = key(item);
    map.set(k, (map.get(k) ?? 0) + 1);
  }
  return map;
}

function groupFindings(findings: Finding[], mode: GroupBy): GroupedFindings[] {
  if (mode === "file") {
    const map = new Map<string, Finding[]>();
    for (const f of findings) {
      const existing = map.get(f.filePath);
      if (existing) {
        existing.push(f);
      } else {
        map.set(f.filePath, [f]);
      }
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([label, items]) => ({ label, findings: sortFindings(items) }));
  }

  if (mode === "severity") {
    const map = new Map<Severity, Finding[]>();
    for (const f of findings) {
      const existing = map.get(f.severity);
      if (existing) {
        existing.push(f);
      } else {
        map.set(f.severity, [f]);
      }
    }
    const order: Record<Severity, number> = { CRITICAL: 0, WARNING: 1, INFO: 2 };
    return Array.from(map.entries())
      .sort(([a], [b]) => order[a] - order[b])
      .map(([label, items]) => ({ label, findings: sortFindings(items) }));
  }

  // category
  const map = new Map<string, Finding[]>();
  for (const f of findings) {
    const existing = map.get(f.category);
    if (existing) {
      existing.push(f);
    } else {
      map.set(f.category, [f]);
    }
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([label, items]) => ({ label, findings: sortFindings(items) }));
}

export function FindingsList({ findings }: FindingsListProps) {
  const [activeSeverities, setActiveSeverities] = useState<Set<Severity>>(
    () => new Set(ALL_SEVERITIES),
  );
  const [activeCategories, setActiveCategories] = useState<Set<string> | null>(null);
  const [groupBy, setGroupBy] = useState<GroupBy>("file");

  const allCategories = useMemo(() => {
    const cats = new Set<string>();
    for (const f of findings) cats.add(f.category);
    return Array.from(cats).sort();
  }, [findings]);

  const allCategoriesSet = useMemo(() => new Set(allCategories), [allCategories]);

  // effectiveCategories is the source of truth: uses activeCategories if set, otherwise all
  const effectiveCategories = useMemo(
    () => activeCategories ?? allCategoriesSet,
    [activeCategories, allCategoriesSet],
  );

  const filteredFindings = useMemo(() => {
    return findings.filter(
      (f) => activeSeverities.has(f.severity) && effectiveCategories.has(f.category),
    );
  }, [findings, activeSeverities, effectiveCategories]);

  const filteredCounts = useMemo(() => {
    const counts: Record<Severity, number> = { CRITICAL: 0, WARNING: 0, INFO: 0 };
    for (const f of filteredFindings) counts[f.severity]++;
    return counts;
  }, [filteredFindings]);

  const filteredCategoryCounts = useMemo(
    () => countBy(filteredFindings, (f) => f.category),
    [filteredFindings],
  );

  const groups = useMemo(
    () => groupFindings(filteredFindings, groupBy),
    [filteredFindings, groupBy],
  );

  const isFiltered =
    activeSeverities.size < ALL_SEVERITIES.length ||
    (allCategories.length > 1 && activeCategories !== null && activeCategories.size < allCategories.length);

  const resetFilters = () => {
    setActiveSeverities(new Set(ALL_SEVERITIES));
    setActiveCategories(null);
  };

  const toggleSeverity = (sev: Severity) => {
    setActiveSeverities((prev) => {
      const next = new Set(prev);
      if (next.has(sev)) {
        if (next.size > 1) next.delete(sev);
      } else {
        next.add(sev);
      }
      return next;
    });
  };

  const toggleCategory = (cat: string) => {
    setActiveCategories((prev) => {
      const current = prev ?? new Set(allCategories);
      const next = new Set(current);
      if (next.has(cat)) {
        if (next.size > 1) next.delete(cat);
      } else {
        next.add(cat);
      }
      return next;
    });
  };

  return (
    <div className="findings-list">
      <div className="findings-list-header">
        <h2>
          Findings{" "}
          <span className="findings-list-count">
            {isFiltered ? `${filteredFindings.length} / ${findings.length}` : findings.length}
          </span>
          {isFiltered && (
            <button className="findings-list-reset" onClick={resetFilters}>
              Reset filters
            </button>
          )}
        </h2>
      </div>

      <div className="findings-list-filters">
        <div className="findings-list-filter-row">
          <span className="findings-list-filter-label">Severity:</span>
          {ALL_SEVERITIES.map((sev) => (
            <button
              key={sev}
              className={`findings-list-filter-btn findings-list-filter-btn--${sev.toLowerCase()} ${activeSeverities.has(sev) ? "" : "findings-list-filter-btn--inactive"}`}
              onClick={() => toggleSeverity(sev)}
            >
              {filteredCounts[sev]} {sev.charAt(0) + sev.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {allCategories.length > 1 && (
          <div className="findings-list-filter-row">
            <span className="findings-list-filter-label">Category:</span>
            {allCategories.map((cat) => (
              <button
                key={cat}
                className={`findings-list-filter-btn findings-list-filter-btn--category ${effectiveCategories.has(cat) ? "" : "findings-list-filter-btn--inactive"}`}
                onClick={() => toggleCategory(cat)}
              >
                {filteredCategoryCounts.get(cat) ?? 0} {cat}
              </button>
            ))}
          </div>
        )}

        <div className="findings-list-filter-row">
          <span className="findings-list-filter-label">Group by:</span>
          <div className="findings-list-groupby">
            {(["file", "severity", "category"] as const).map((mode) => (
              <label key={mode} className="findings-list-groupby-option">
                <input
                  type="radio"
                  name="findings-groupby"
                  checked={groupBy === mode}
                  onChange={() => setGroupBy(mode)}
                />
                <span>{mode.charAt(0).toUpperCase() + mode.slice(1)}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      {filteredFindings.length === 0 ? (
        <p className="findings-list-empty">
          {findings.length === 0
            ? "No issues found. Code looks clean!"
            : "No findings match the current filters. "}
          {findings.length > 0 && (
            <button className="findings-list-reset-inline" onClick={resetFilters}>
              Reset filters
            </button>
          )}
        </p>
      ) : (
        <div className="findings-list-groups">
          {groups.map((group) => (
            <GroupSection key={group.label} group={group} groupBy={groupBy} />
          ))}
        </div>
      )}
    </div>
  );
}

function GroupSection({ group, groupBy }: { group: GroupedFindings; groupBy: GroupBy }) {
  const [isExpanded, setIsExpanded] = useState(true);

  const headerLabel =
    groupBy === "file"
      ? group.label
      : groupBy === "severity"
        ? group.label.charAt(0) + group.label.slice(1).toLowerCase()
        : group.label;

  return (
    <div className="findings-file-group">
      <button
        className={`findings-file-header ${groupBy === "severity" ? `findings-file-header--${group.label.toLowerCase()}` : ""}`}
        onClick={() => setIsExpanded(!isExpanded)}
        aria-expanded={isExpanded}
      >
        <span className="findings-file-chevron">{isExpanded ? "\u25BC" : "\u25B6"}</span>
        <span className="findings-file-name">{headerLabel}</span>
        <span className="findings-file-count">
          {group.findings.length} finding{group.findings.length !== 1 ? "s" : ""}
        </span>
      </button>
      {isExpanded && (
        <div className="findings-file-items">
          {group.findings.map((f) => (
            <FindingCard key={f.id} finding={f} />
          ))}
        </div>
      )}
    </div>
  );
}

function FindingCard({ finding }: { finding: Finding }) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <button
      className={`findings-card findings-card--${finding.severity.toLowerCase()}`}
      onClick={() => setIsExpanded(!isExpanded)}
      aria-expanded={isExpanded}
    >
      <div className="findings-card-header">
        <span className={`findings-card-severity findings-card-severity--${finding.severity.toLowerCase()}`}>
          {finding.severity}
        </span>
        <span className="findings-card-category">{finding.category}</span>
        {finding.lineNumber != null && (
          <span className="findings-card-line">line {finding.lineNumber}</span>
        )}
        <span className="findings-card-expand">{isExpanded ? "\u25B2" : "\u25BC"}</span>
      </div>
      {isExpanded && (
        <div className="findings-card-body">
          <p className="findings-card-file">{finding.filePath}{finding.lineNumber != null ? `:${finding.lineNumber}` : ""}</p>
          <p className="findings-card-desc">{finding.description}</p>
          {finding.suggestion && (
            <p className="findings-card-suggestion">{finding.suggestion}</p>
          )}
        </div>
      )}
    </button>
  );
}
