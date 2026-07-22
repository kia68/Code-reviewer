export interface Project {
  id: number;
  name: string;
  createdAt: string;
}

export type ReviewStatus = "IN_PROGRESS" | "COMPLETED" | "FAILED";

export interface ReviewRun {
  id: number;
  projectId: number;
  status: ReviewStatus;
  triggeredAt: string;
  completedAt: string | null;
  fileCount: number | null;
  totalSizeBytes: number | null;
}

export type Severity = "INFO" | "WARNING" | "CRITICAL";

export interface Finding {
  id: number;
  reviewRunId: number;
  filePath: string;
  lineNumber: number;
  category: string;
  severity: Severity;
  description: string;
  suggestion: string | null;
  // Origin of the finding as reported by the backend: "LLM" (KI review) or
  // "AST" (static analysis). Optional: may be missing on findings persisted
  // before the field existed.
  source?: string | null;
}

export class CodeReviewerApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = "CodeReviewerApiError";
  }
}

export class CodeReviewerApiClient {
  constructor(private readonly baseUrl: string) {}

  async findOrCreateProject(name: string): Promise<Project> {
    const existing = await this.listProjects();
    const found = existing.find((project) => project.name === name);
    return found ?? this.createProject(name);
  }

  async listProjects(): Promise<Project[]> {
    const response = await fetch(`${this.baseUrl}/api/projects`);
    if (!response.ok) {
      throw new CodeReviewerApiError(await this.extractErrorMessage(response), response.status);
    }
    return (await response.json()) as Project[];
  }

  async createProject(name: string): Promise<Project> {
    const response = await fetch(`${this.baseUrl}/api/projects`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
    if (!response.ok) {
      throw new CodeReviewerApiError(await this.extractErrorMessage(response), response.status);
    }
    return (await response.json()) as Project;
  }

  async createReviewRun(projectId: number, fileName: string, content: string): Promise<ReviewRun> {
    const form = new FormData();
    form.append("file", new Blob([content], { type: "text/plain" }), fileName);

    const response = await fetch(`${this.baseUrl}/api/projects/${projectId}/review-runs`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      throw new CodeReviewerApiError(await this.extractErrorMessage(response), response.status);
    }
    return (await response.json()) as ReviewRun;
  }

  async analyzeFindings(projectId: number, reviewRunId: number): Promise<Finding[]> {
    const response = await fetch(`${this.baseUrl}/api/projects/${projectId}/review-runs/${reviewRunId}/findings`, {
      method: "POST",
    });
    if (!response.ok) {
      throw new CodeReviewerApiError(await this.extractErrorMessage(response), response.status);
    }
    return (await response.json()) as Finding[];
  }

  private async extractErrorMessage(response: Response): Promise<string> {
    try {
      const body = (await response.json()) as { message?: string };
      return body.message ?? `Backend-Fehler (${response.status})`;
    } catch {
      return `Backend-Fehler (${response.status})`;
    }
  }
}