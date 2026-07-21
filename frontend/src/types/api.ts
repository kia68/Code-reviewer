export type ReviewStatus = "IN_PROGRESS" | "COMPLETED" | "FAILED";

export type Severity = "INFO" | "WARNING" | "CRITICAL";

export interface Project {
  id: number;
  name: string;
  createdAt: string;
}

export interface ReviewRun {
  id: number;
  projectId: number;
  status: ReviewStatus;
  triggeredAt: string;
  completedAt: string | null;
  fileCount: number | null;
  totalSizeBytes: number | null;
  parentRunId: number | null;
}

export interface StoredFileDto {
  id: number;
  filePath: string;
  sizeBytes: number;
  content?: string;
}

export interface Finding {
  id: number;
  reviewRunId: number;
  filePath: string;
  lineNumber: number | null;
  category: string;
  severity: Severity;
  description: string;
  suggestion: string;
}

export interface ApiError {
  status: number;
  message: string;
}
