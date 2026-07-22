import type { Finding, ReviewRun, StoredFileDto } from "../types/api";
import { apiClient } from "./client";

export async function uploadFile(projectId: number, file: File): Promise<ReviewRun> {
  return apiClient.uploadFile<ReviewRun>(`/projects/${projectId}/review-runs`, file);
}

export async function getReviewRuns(projectId: number): Promise<ReviewRun[]> {
  return apiClient.get<ReviewRun[]>(`/projects/${projectId}/review-runs`);
}

export async function getReviewRun(projectId: number, runId: number): Promise<ReviewRun> {
  return apiClient.get<ReviewRun>(`/projects/${projectId}/review-runs/${runId}`);
}

export async function getFindings(projectId: number, runId: number): Promise<Finding[]> {
  return apiClient.get<Finding[]>(`/projects/${projectId}/review-runs/${runId}/findings`);
}

export async function analyzeFindings(projectId: number, runId: number): Promise<Finding[]> {
  return apiClient.post<Finding[]>(`/projects/${projectId}/review-runs/${runId}/findings`);
}

export async function listFiles(projectId: number, runId: number): Promise<StoredFileDto[]> {
  return apiClient.get<StoredFileDto[]>(`/projects/${projectId}/review-runs/${runId}/files`);
}

export async function readFile(projectId: number, runId: number, filePath: string): Promise<StoredFileDto> {
  const encoded = encodeURIComponent(filePath);
  return apiClient.get<StoredFileDto>(
    `/projects/${projectId}/review-runs/${runId}/files/content?path=${encoded}`,
  );
}

export async function createNewVersion(
  projectId: number,
  parentRunId: number,
  files: StoredFileDto[],
): Promise<ReviewRun> {
  return apiClient.post<ReviewRun>(
    `/projects/${projectId}/review-runs/${parentRunId}/new-version`,
    files,
  );
}
