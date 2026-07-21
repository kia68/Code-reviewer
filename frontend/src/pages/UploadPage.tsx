import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, Link } from "react-router";
import { getProject } from "../api/projects";
import { uploadFile, getReviewRuns } from "../api/review-runs";
import { FileUpload } from "../components/FileUpload";
import { ApiClientError } from "../api/client";
import type { ReviewRun, Finding } from "../types/api";
import { apiClient } from "../api/client";
import "./UploadPage.css";

export function UploadPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const id = Number(projectId);
  const queryClient = useQueryClient();
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [lastCreatedRun, setLastCreatedRun] = useState<ReviewRun | null>(null);
  const [findings, setFindings] = useState<Finding[] | null>(null);

  const { data: project, isLoading: projectLoading } = useQuery({
    queryKey: ["project", id],
    queryFn: () => getProject(id),
    enabled: !isNaN(id),
  });

  const { data: reviewRuns } = useQuery({
    queryKey: ["reviewRuns", id],
    queryFn: () => getReviewRuns(id),
    enabled: !isNaN(id),
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadFile(id, file),
    onSuccess: (run) => {
      setUploadError(null);
      setLastCreatedRun(run);
      setFindings(null);
      queryClient.invalidateQueries({ queryKey: ["reviewRuns", id] });
    },
    onError: (error: ApiClientError) => {
      setUploadError(error.message);
      setLastCreatedRun(null);
      setFindings(null);
    },
  });

  const analyzeMutation = useMutation({
    mutationFn: (runId: number) =>
      apiClient.post<Finding[]>(`/projects/${id}/review-runs/${runId}/findings`),
    onSuccess: (data) => {
      setFindings(data);
    },
  });

  const handleUpload = (file: File) => {
    setUploadError(null);
    setFindings(null);
    uploadMutation.mutate(file);
  };

  const handleAnalyze = () => {
    if (!lastCreatedRun) return;
    analyzeMutation.mutate(lastCreatedRun.id);
  };

  if (projectLoading) return <p>Loading project...</p>;
  if (!project) return <p>Project not found.</p>;

  return (
    <div>
      <Link to="/" className="upload-page-back">
        &larr; All projects
      </Link>
      <h1>{project.name}</h1>

      <FileUpload onUpload={handleUpload} isUploading={uploadMutation.isPending} />

      {uploadError && <p className="upload-page-error">{uploadError}</p>}

      {lastCreatedRun && (
        <div className="upload-page-result">
          <div className="upload-page-status">
            <span className={`upload-page-badge upload-page-badge--${lastCreatedRun.status.toLowerCase()}`}>
              {lastCreatedRun.status}
            </span>
            <span className="upload-page-meta">
              {lastCreatedRun.fileCount} file{lastCreatedRun.fileCount !== 1 ? "s" : ""}
              {lastCreatedRun.totalSizeBytes != null &&
                ` · ${(lastCreatedRun.totalSizeBytes / 1024).toFixed(1)} KB`}
            </span>
          </div>

          {lastCreatedRun.status === "COMPLETED" && !findings && (
            <button
              className="upload-page-analyze"
              onClick={handleAnalyze}
              disabled={analyzeMutation.isPending}
            >
              {analyzeMutation.isPending ? "Analyzing..." : "Analyze Code"}
            </button>
          )}

          {analyzeMutation.error && (
            <p className="upload-page-error">
              {(analyzeMutation.error as ApiClientError).message}
            </p>
          )}
        </div>
      )}

      {findings && (
        <div className="upload-page-findings">
          <h2>
            Findings{" "}
            <span className="upload-page-findings-count">{findings.length}</span>
          </h2>
          {findings.length === 0 ? (
            <p>No issues found. Code looks clean!</p>
          ) : (
            <ul className="upload-page-findings-list">
              {findings.map((f) => (
                <li key={f.id} className={`upload-page-finding upload-page-finding--${f.severity.toLowerCase()}`}>
                  <div className="upload-page-finding-header">
                    <span className={`upload-page-severity upload-page-severity--${f.severity.toLowerCase()}`}>
                      {f.severity}
                    </span>
                    <span className="upload-page-finding-category">{f.category}</span>
                    <span className="upload-page-finding-file">
                      {f.filePath}
                      {f.lineNumber != null ? `:${f.lineNumber}` : ""}
                    </span>
                  </div>
                  <p className="upload-page-finding-desc">{f.description}</p>
                  {f.suggestion && (
                    <p className="upload-page-finding-suggestion">{f.suggestion}</p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {reviewRuns && reviewRuns.length > 0 && (
        <div className="upload-page-history">
          <h2>Review History</h2>
          <ul className="upload-page-history-list">
            {reviewRuns.map((run) => (
              <li key={run.id} className="upload-page-history-item">
                <span className={`upload-page-badge upload-page-badge--${run.status.toLowerCase()}`}>
                  {run.status}
                </span>
                <span className="upload-page-history-date">
                  {new Date(run.triggeredAt).toLocaleString()}
                </span>
                <span className="upload-page-history-meta">
                  {run.fileCount ?? 0} files
                  {run.totalSizeBytes != null && ` · ${(run.totalSizeBytes / 1024).toFixed(1)} KB`}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
