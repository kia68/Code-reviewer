import { beforeEach, describe, expect, it, vi } from "vitest";
import { CodeReviewerApiClient, CodeReviewerApiError } from "../src/apiClient";

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

describe("CodeReviewerApiClient", () => {
  const baseUrl = "http://localhost:8080";
  let client: CodeReviewerApiClient;

  beforeEach(() => {
    client = new CodeReviewerApiClient(baseUrl);
    vi.restoreAllMocks();
  });

  it("findOrCreateProject returns an existing project by name without creating one", async () => {
    const existing = [{ id: 1, name: "Demo", createdAt: "2026-01-01T00:00:00" }];
    global.fetch = vi.fn().mockResolvedValueOnce(jsonResponse(existing));

    const project = await client.findOrCreateProject("Demo");

    expect(project.id).toBe(1);
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  it("findOrCreateProject creates a new project when none matches", async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ id: 2, name: "New", createdAt: "2026-01-01T00:00:00" }));

    const project = await client.findOrCreateProject("New");

    expect(project.id).toBe(2);
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it("createReviewRun sends multipart form data and returns the created run", async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      jsonResponse({
        id: 5,
        projectId: 1,
        status: "COMPLETED",
        triggeredAt: "2026-01-01T00:00:00",
        completedAt: null,
        fileCount: 1,
        totalSizeBytes: 10,
      }),
    );

    const run = await client.createReviewRun(1, "Hello.java", "class Hello {}");

    expect(run.id).toBe(5);
    const [url, init] = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe(`${baseUrl}/api/projects/1/review-runs`);
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
  });

  it("analyzeFindings posts to the findings endpoint and returns the findings", async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      jsonResponse([
        {
          id: 1,
          reviewRunId: 5,
          filePath: "Hello.java",
          lineNumber: 2,
          category: "LONG_METHOD",
          severity: "WARNING",
          description: "x",
          suggestion: "y",
        },
      ]),
    );

    const findings = await client.analyzeFindings(1, 5);

    expect(findings).toHaveLength(1);
    expect(findings[0].category).toBe("LONG_METHOD");
    const [url, init] = (global.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe(`${baseUrl}/api/projects/1/review-runs/5/findings`);
    expect(init.method).toBe("POST");
  });

  it("throws CodeReviewerApiError with the backend's message on failure", async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      jsonResponse({ message: "Nicht unterstütztes Dateiformat" }, 400),
    );

    await expect(client.createReviewRun(1, "x.txt", "noop")).rejects.toThrow(CodeReviewerApiError);
  });

  it("falls back to a generic message when the error body has no message field", async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(jsonResponse({}, 500));

    await expect(client.listProjects()).rejects.toThrow("Backend-Fehler (500)");
  });
});