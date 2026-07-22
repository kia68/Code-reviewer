import { useCallback, useRef, useState } from "react";
import { validateFile, MAX_SINGLE_FILE_MB, MAX_ZIP_MB } from "../utils/fileValidation";
import "./FileUpload.css";

interface FileUploadProps {
  onUpload: (file: File) => void;
  isUploading: boolean;
}

export function FileUpload({ onUpload, isUploading }: FileUploadProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFile = useCallback((file: File) => {
    setValidationError(null);
    const error = validateFile(file);
    if (error) {
      setValidationError(error.message);
      setSelectedFile(null);
      return;
    }
    setSelectedFile(file);
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      const file = e.dataTransfer.files[0];
      if (file) handleFile(file);
    },
    [handleFile],
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) handleFile(file);
    },
    [handleFile],
  );

  const handleClick = () => {
    inputRef.current?.click();
  };

  const handleSubmit = () => {
    if (selectedFile) onUpload(selectedFile);
  };

  return (
    <div className="file-upload">
      <div
        className={`file-upload-dropzone ${isDragging ? "file-upload-dropzone--active" : ""}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={handleClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") handleClick();
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".java,.zip"
          onChange={handleInputChange}
          className="file-upload-input"
        />
        <div className="file-upload-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="17 8 12 3 7 8" />
            <line x1="12" y1="3" x2="12" y2="15" />
          </svg>
        </div>
        <p className="file-upload-text">
          Drop a <strong>.java</strong> or <strong>.zip</strong> file here, or click to browse
        </p>
        <p className="file-upload-hint">
          Max {MAX_SINGLE_FILE_MB} MB for .java, {MAX_ZIP_MB} MB for .zip
        </p>
      </div>

      {validationError && <p className="file-upload-error">{validationError}</p>}

      {selectedFile && !validationError && (
        <div className="file-upload-selected">
          <span className="file-upload-filename">{selectedFile.name}</span>
          <span className="file-upload-size">{(selectedFile.size / 1024).toFixed(1)} KB</span>
          <button
            className="file-upload-submit"
            onClick={handleSubmit}
            disabled={isUploading}
          >
            {isUploading ? "Uploading..." : "Upload"}
          </button>
        </div>
      )}
    </div>
  );
}
