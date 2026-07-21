import { useState, useCallback, useEffect, useMemo, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, Link } from "react-router";
import { getProject } from "../api/projects";
import {
  uploadFile,
  getReviewRuns,
  getFindings,
  listFiles,
  readFile,
  createNewVersion,
} from "../api/review-runs";
import { FileUpload } from "../components/FileUpload";
import { CodeEditor } from "../components/CodeEditor";
import { FileExplorer, type DraftFile } from "../components/FileExplorer";
import { FindingsList } from "../components/FindingsList";
import { ApiClientError } from "../api/client";
import type { ReviewRun, Finding } from "../types/api";
import { apiClient } from "../api/client";
import "./UploadPage.css";

type InputMode = "upload" | "editor";

export function UploadPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const id = Number(projectId);
  const queryClient = useQueryClient();

  const [inputMode, setInputMode] = useState<InputMode>("upload");
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [lastCreatedRun, setLastCreatedRun] = useState<ReviewRun | null>(null);
  const [findings, setFindings] = useState<Finding[] | null>(null);
  const [filteredFindings, setFilteredFindings] = useState<Finding[] | null>(null);

  // Editor editing state
  const [selectedRun, setSelectedRun] = useState<ReviewRun | null>(null);
  const [draftFiles, setDraftFiles] = useState<DraftFile[]>([]);
  const [selectedFilePath, setSelectedFilePath] = useState<string | null>(null);

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

  const loadRunIntoEditor = useCallback(
    async (run: ReviewRun) => {
      setSelectedRun(run);
      setInputMode("editor");
      try {
        const files = await listFiles(id, run.id);
        const drafts: DraftFile[] = [];
        for (const f of files) {
          const full = await readFile(id, run.id, f.filePath);
          drafts.push({
            filePath: full.filePath,
            content: full.content ?? "",
            sizeBytes: full.sizeBytes,
          });
        }
        setDraftFiles(drafts);
        if (drafts.length > 0) {
          setSelectedFilePath(drafts[0].filePath);
        }
      } catch {
        setUploadError("Failed to load files for this review run.");
      }
    },
    [id],
  );

  const handleUpload = (file: File) => {
    setUploadError(null);
    setFindings(null);
    uploadMutation.mutate(file);
  };

  const handleEditorSubmit = async () => {
    if (draftFiles.length === 0) return;
    setUploadError(null);
    setFindings(null);
    setFilteredFindings(null);
    let file: File;
    if (draftFiles.length === 1) {
      const f = draftFiles[0];
      file = new File([f.content], f.filePath, { type: "text/x-java" });
    } else {
      const JSZip = (await import("jszip")).default;
      const zip = new JSZip();
      for (const f of draftFiles) {
        zip.file(f.filePath, f.content);
      }
      const blob = await zip.generateAsync({ type: "blob" });
      file = new File([blob], "project.zip", { type: "application/zip" });
    }
    uploadMutation.mutate(file, {
      onSuccess: (run) => {
        loadRunIntoEditor(run);
        analyzeMutation.mutate(run.id);
      },
    });
  };

  const handleAnalyze = () => {
    if (!lastCreatedRun) return;
    const shouldTransition = !selectedRun;
    analyzeMutation.mutate(lastCreatedRun.id, {
      onSuccess: () => {
        if (shouldTransition) {
          loadRunIntoEditor(lastCreatedRun);
        }
      },
    });
  };

  const handleModeSwitch = (mode: InputMode) => {
    setInputMode(mode);
    setUploadError(null);
    setFindings(null);
    setFilteredFindings(null);
    setLastCreatedRun(null);
    if (mode === "upload") {
      setSelectedRun(null);
      setDraftFiles([]);
      setSelectedFilePath(null);
    } else if (mode === "editor" && draftFiles.length === 0) {
      const defaultFile: DraftFile = { filePath: "Untitled.java", content: "", sizeBytes: 0 };
      setDraftFiles([defaultFile]);
      setSelectedFilePath(defaultFile.filePath);
    }
  };

  // Open a review run in the editor
  const handleOpenRun = useCallback(
    async (run: ReviewRun) => {
      setUploadError(null);
      setFindings(null);
      setFilteredFindings(null);
      setLastCreatedRun(null);
      await loadRunIntoEditor(run);
      if (run.status === "COMPLETED") {
        try {
          const runFindings = await getFindings(id, run.id);
          setFindings(runFindings);
        } catch {
          // findings not available, that's OK
        }
      }
    },
    [id, loadRunIntoEditor],
  );

  const hasAutoOpened = useRef(false);
  useEffect(() => {
    if (hasAutoOpened.current || !reviewRuns || reviewRuns.length === 0) return;
    hasAutoOpened.current = true;
    const latest = reviewRuns.reduce((a, b) =>
      new Date(a.triggeredAt) > new Date(b.triggeredAt) ? a : b,
    );
    handleOpenRun(latest);
  }, [reviewRuns, handleOpenRun]);

  // FileExplorer callbacks
  const handleSelectFile = (path: string) => {
    setSelectedFilePath(path);
  };

  const handleAddFile = (path: string) => {
    setDraftFiles((prev) => [...prev, { filePath: path, content: "", sizeBytes: 0 }]);
    setSelectedFilePath(path);
  };

  const handleDeleteFile = (path: string) => {
    setDraftFiles((prev) => prev.filter((f) => f.filePath !== path));
    if (selectedFilePath === path) {
      setSelectedFilePath(() => {
        const remaining = draftFiles.filter((f) => f.filePath !== path);
        return remaining.length > 0 ? remaining[0].filePath : null;
      });
    }
  };

  const handleImportFiles = (imported: DraftFile[]) => {
    setDraftFiles((prev) => [...prev, ...imported]);
    setSelectedFilePath(imported[0].filePath);
  };

  // Track code changes from editor
  const handleCodeChange = useCallback(
    (content: string) => {
      if (!selectedFilePath) return;
      setDraftFiles((prev) =>
        prev.map((f) =>
          f.filePath === selectedFilePath
            ? { ...f, content, sizeBytes: new Blob([content]).size }
            : f,
        ),
      );
    },
    [selectedFilePath],
  );

  // Save as new version
  const saveMutation = useMutation({
    mutationFn: () => {
      if (!selectedRun) throw new Error("No run selected");
      const files = draftFiles.map((f) => ({
        id: 0,
        filePath: f.filePath,
        sizeBytes: f.sizeBytes,
        content: f.content,
      }));
      return createNewVersion(id, selectedRun.id, files);
    },
    onSuccess: (newRun) => {
      setSelectedRun(newRun);
      setLastCreatedRun(newRun);
      setFindings(null);
      setFilteredFindings(null);
      queryClient.invalidateQueries({ queryKey: ["reviewRuns", id] });
      analyzeMutation.mutate(newRun.id);
    },
    onError: (error: ApiClientError) => {
      setUploadError(error.message);
    },
  });

  const handleSave = () => {
    saveMutation.mutate();
  };

  const isEditing = selectedRun !== null;

  const fileFindings = useMemo(() => {
    if (!filteredFindings || !selectedFilePath) return [];
    return filteredFindings.filter((f) => f.filePath === selectedFilePath);
  }, [filteredFindings, selectedFilePath]);

  useEffect(() => {
    if (draftFiles.length > 0 && !draftFiles.some((f) => f.filePath === selectedFilePath)) {
      setSelectedFilePath(draftFiles[0].filePath);
    }
  }, [draftFiles, selectedFilePath]);

  if (projectLoading) return <p>Loading project...</p>;
  if (!project) return <p>Project not found.</p>;

  return (
    <div>
      <Link to="/" className="upload-page-back">
        &larr; All projects
      </Link>
      <h1>{project.name}</h1>

      {!isEditing && (
        <div className="upload-page-tabs">
          <button
            className={`upload-page-tab ${inputMode === "upload" ? "upload-page-tab--active" : ""}`}
            onClick={() => handleModeSwitch("upload")}
          >
            Upload File
          </button>
          <button
            className={`upload-page-tab ${inputMode === "editor" ? "upload-page-tab--active" : ""}`}
            onClick={() => handleModeSwitch("editor")}
          >
            Write Code
          </button>
        </div>
      )}

      {inputMode === "upload" ? (
        <FileUpload onUpload={handleUpload} isUploading={uploadMutation.isPending} />
      ) : (
        <div className="upload-page-editor-layout">
          <FileExplorer
            files={draftFiles}
            selectedFile={selectedFilePath}
            onSelectFile={handleSelectFile}
            onAddFile={handleAddFile}
            onDeleteFile={handleDeleteFile}
            onImportFiles={handleImportFiles}
          />
          <div className="upload-page-editor-main">
            {selectedFilePath && (
              <CodeEditor
                onSubmit={handleEditorSubmit}
                isUploading={uploadMutation.isPending || analyzeMutation.isPending}
                markers={fileFindings}
                fileContent={
                  draftFiles.find((f) => f.filePath === selectedFilePath)?.content
                }
                fileName={selectedFilePath}
                isEditing={isEditing}
                onSave={isEditing ? handleSave : undefined}
                isSaving={saveMutation.isPending}
                onCodeChange={handleCodeChange}
              />
            )}
          </div>
        </div>
      )}

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

          {lastCreatedRun.status === "COMPLETED" && !isEditing && (
            <button
              className="upload-page-analyze"
              onClick={handleAnalyze}
              disabled={analyzeMutation.isPending}
            >
              {analyzeMutation.isPending ? "Analyzing..." : findings ? "Re-analyze Code" : "Analyze Code"}
            </button>
          )}

          {analyzeMutation.error && (
            <p className="upload-page-error">
              {(analyzeMutation.error as ApiClientError).message}
            </p>
          )}
        </div>
      )}

      {findings && <FindingsList findings={findings} onFilteredFindingsChange={setFilteredFindings} />}

      {reviewRuns && reviewRuns.length > 0 && (
        <div className="upload-page-history">
          <h2>Review History</h2>
          <ul className="upload-page-history-list">
            {reviewRuns.map((run) => (
              <li key={run.id} className="upload-page-history-item">
                <button
                  className="upload-page-history-open"
                  onClick={() => handleOpenRun(run)}
                  title="Open in editor"
                >
                  Open
                </button>
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
                {run.parentRunId != null && (
                  <span className="upload-page-history-version">v{run.id}</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
