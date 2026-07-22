import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { apiClient, ApiClientError } from "./client";

function mockFetch(response: Response) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));
}

function fakeResponse(body: unknown, init: { status?: number; statusText?: string } = {}): Response {
  const { status = 200, statusText = "OK" } = init;
  const isOk = status >= 200 && status < 300;
  const response = {
    ok: isOk,
    status,
    statusText,
    json: () => Promise.resolve(body),
  } as unknown as Response;
  return response;
}

describe("apiClient", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("get() returns parsed JSON on success", async () => {
    const data = { id: 1, name: "Test" };
    mockFetch(fakeResponse(data));

    const result = await apiClient.get("/projects/1");
    expect(result).toEqual(data);
    expect(fetch).toHaveBeenCalledWith("/api/projects/1");
  });

  it("post() sends JSON body with correct headers", async () => {
    const data = { id: 1, name: "New" };
    mockFetch(fakeResponse(data, { status: 201, statusText: "Created" }));

    const result = await apiClient.post("/projects", { name: "New" });
    expect(result).toEqual(data);

    const call = vi.mocked(fetch).mock.calls[0];
    expect(call[0]).toBe("/api/projects");
    expect((call[1]!.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
    expect(call[1]!.body).toBe(JSON.stringify({ name: "New" }));
  });

  it("throws ApiClientError with message from response body", async () => {
    mockFetch(fakeResponse({ message: "Project not found" }, { status: 404, statusText: "Not Found" }));

    try {
      await apiClient.get("/projects/999");
      expect.fail("Should have thrown");
    } catch (e) {
      expect(e).toBeInstanceOf(ApiClientError);
      expect((e as ApiClientError).status).toBe(404);
      expect((e as ApiClientError).message).toBe("Project not found");
    }
  });

  it("falls back to statusText when response body is not JSON", async () => {
    const response = {
      ok: false,
      status: 404,
      statusText: "Not Found",
      json: () => Promise.reject(new Error("Not JSON")),
    } as unknown as Response;
    mockFetch(response);

    try {
      await apiClient.get("/unknown");
      expect.fail("Should have thrown");
    } catch (e) {
      expect(e).toBeInstanceOf(ApiClientError);
      expect((e as ApiClientError).status).toBe(404);
      expect((e as ApiClientError).message).toBe("Not Found");
    }
  });

  it("returns undefined for 204 No Content", async () => {
    mockFetch(fakeResponse(null, { status: 204, statusText: "No Content" }));

    const result = await apiClient.get("/health");
    expect(result).toBeUndefined();
  });

  it("uploadFile sends FormData without Content-Type header", async () => {
    const data = { id: 1 };
    mockFetch(fakeResponse(data));

    const file = new File(["content"], "test.java", { type: "application/octet-stream" });
    const result = await apiClient.uploadFile("/projects/1/review-runs", file);
    expect(result).toEqual(data);

    const call = vi.mocked(fetch).mock.calls[0];
    expect(call[0]).toBe("/api/projects/1/review-runs");
    expect(call[1]!.body).toBeInstanceOf(FormData);
    expect(call[1]!.headers).toBeUndefined();
  });
});
