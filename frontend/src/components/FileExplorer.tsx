import { useState, useRef } from "react";
import JSZip from "jszip";
import "./FileExplorer.css";

export interface DraftFile {
  filePath: string;
  content: string;
  sizeBytes: number;
}

interface FileExplorerProps {
  files: DraftFile[];
  selectedFile: string | null;
  onSelectFile: (path: string) => void;
  onAddFile: (path: string) => void;
  onDeleteFile: (path: string) => void;
  onImportFiles?: (files: DraftFile[]) => void;
}

function baseName(path: string): string {
  const parts = path.replace(/\\/g, "/").split("/");
  return parts[parts.length - 1] ?? path;
}

export function FileExplorer({
  files,
  selectedFile,
  onSelectFile,
  onAddFile,
  onDeleteFile,
  onImportFiles,
}: FileExplorerProps) {
  const [isAdding, setIsAdding] = useState(false);
  const [newFileName, setNewFileName] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleAdd = () => {
    const name = newFileName.trim();
    if (!name) return;
    const filePath = name.endsWith(".java") ? name : name + ".java";
    if (files.some((f) => f.filePath === filePath)) return;
    onAddFile(filePath);
    setNewFileName("");
    setIsAdding(false);
  };

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const inputFiles = e.target.files;
    if (!inputFiles || inputFiles.length === 0 || !onImportFiles) return;

    const existing = new Set(files.map((f) => f.filePath));
    const imported: DraftFile[] = [];

    for (const file of Array.from(inputFiles)) {
      if (file.name.endsWith(".zip")) {
        const buffer = await file.arrayBuffer();
        const zip = await JSZip.loadAsync(buffer);
        for (const [zipPath, zipEntry] of Object.entries(zip.files)) {
          if (zipEntry.dir) continue;
          const name = baseName(zipPath);
          if (!name.endsWith(".java") || existing.has(name)) continue;
          const content = await zipEntry.async("string");
          existing.add(name);
          imported.push({ filePath: name, content, sizeBytes: new Blob([content]).size });
        }
      } else if (file.name.endsWith(".java") && !existing.has(file.name)) {
        const content = await file.text();
        existing.add(file.name);
        imported.push({ filePath: file.name, content, sizeBytes: new Blob([content]).size });
      }
    }

    if (imported.length > 0) {
      onImportFiles(imported);
    }

    e.target.value = "";
  };

  return (
    <div className="file-explorer">
      <div className="file-explorer-header">
        <span className="file-explorer-title">Files</span>
        <div className="file-explorer-header-actions">
          {onImportFiles && (
            <button
              className="file-explorer-import-btn"
              onClick={() => fileInputRef.current?.click()}
              aria-label="Import files"
              title="Import files from machine"
            >
              ↑
            </button>
          )}
          <button
            className="file-explorer-add-btn"
            onClick={() => setIsAdding(!isAdding)}
            aria-label="Add file"
          >
            +
          </button>
        </div>
      </div>

      {onImportFiles && (
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept=".java,.zip"
          className="file-explorer-hidden-input"
          onChange={handleImport}
        />
      )}

      {isAdding && (
        <div className="file-explorer-new-file">
          <input
            className="file-explorer-input"
            value={newFileName}
            onChange={(e) => setNewFileName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleAdd();
              if (e.key === "Escape") setIsAdding(false);
            }}
            placeholder="FileName.java"
            autoFocus
          />
          <button className="file-explorer-confirm-btn" onClick={handleAdd}>
            Add
          </button>
        </div>
      )}

      <ul className="file-explorer-list">
        {files.map((f) => (
          <li
            key={f.filePath}
            className={`file-explorer-item ${f.filePath === selectedFile ? "file-explorer-item--selected" : ""}`}
          >
            <button
              className="file-explorer-item-btn"
              onClick={() => onSelectFile(f.filePath)}
            >
              {f.filePath}
            </button>
            <button
              className="file-explorer-delete-btn"
              onClick={() => onDeleteFile(f.filePath)}
              aria-label={`Delete ${f.filePath}`}
            >
              ×
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
