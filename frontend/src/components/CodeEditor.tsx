import { useState, useRef, useEffect } from "react";
import Editor, { type Monaco } from "@monaco-editor/react";
import type { editor as MonacoEditor } from "monaco-editor";
import type { Finding } from "../types/api";
import "./CodeEditor.css";

interface CodeEditorProps {
  onSubmit: () => void;
  isUploading: boolean;
  markers?: Finding[] | null;
  fileContent?: string;
  fileName?: string;
  isEditing?: boolean;
  onSave?: () => void;
  isSaving?: boolean;
  onCodeChange?: (content: string) => void;
}

export function CodeEditor({
  onSubmit,
  isUploading,
  markers,
  fileContent,
  fileName,
  isEditing = false,
  onSave,
  isSaving = false,
  onCodeChange,
}: CodeEditorProps) {
  const [code, setCode] = useState("");
  const [filename, setFilename] = useState("Untitled.java");
  const [monacoReady, setMonacoReady] = useState(false);
  const editorRef = useRef<MonacoEditor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<Monaco | null>(null);

  const isManagedExternally = fileName != null;

  useEffect(() => {
    if (isManagedExternally && fileContent != null) {
      setCode(fileContent);
    }
    if (isManagedExternally) {
      setFilename(fileName);
    }
  }, [fileContent, fileName, isManagedExternally]);

  useEffect(() => {
    if (!editorRef.current || !monacoRef.current || !markers) return;
    const model = editorRef.current.getModel();
    if (!model) return;

    const monacoMarkers: MonacoEditor.IMarkerData[] = markers
      .filter((f) => f.lineNumber != null)
      .map((f) => ({
        severity:
          f.severity === "CRITICAL"
            ? monacoRef.current!.MarkerSeverity.Error
            : f.severity === "WARNING"
              ? monacoRef.current!.MarkerSeverity.Warning
              : monacoRef.current!.MarkerSeverity.Info,
        startLineNumber: f.lineNumber!,
        startColumn: 1,
        endLineNumber: f.lineNumber!,
        endColumn: Number.MAX_VALUE,
        message: `[${f.source === "LLM" ? "AI" : f.source}] ${f.category}: ${f.description}`,
      }));

    monacoRef.current.editor.setModelMarkers(model, "code-reviewer", monacoMarkers);
  }, [markers, fileName, code, monacoReady]);

  const handleCodeChange = (value: string | undefined) => {
    const next = value ?? "";
    setCode(next);
    onCodeChange?.(next);
  };

  const isBusy = isUploading || isSaving;

  return (
    <div className="code-editor">
      <div className="code-editor-toolbar">
        {isManagedExternally ? (
          <span className="code-editor-filename-label">{filename}</span>
        ) : (
          <input
            className="code-editor-filename"
            value={filename}
            onChange={(e) => setFilename(e.target.value)}
            spellCheck={false}
            aria-label="Filename"
          />
        )}
        {isEditing ? (
          <button
            className="upload-page-analyze"
            onClick={onSave}
            disabled={isBusy || !code.trim()}
          >
            {isSaving ? "Saving..." : "Save as new version"}
          </button>
        ) : (
          <button
            className="upload-page-analyze"
            onClick={() => onSubmit()}
            disabled={isBusy || !code.trim()}
          >
            {isUploading ? "Analyzing..." : "Analyze Code"}
          </button>
        )}
      </div>
      <div className="code-editor-container">
        <Editor
          height="400px"
          language="java"
          theme="vs-dark"
          value={code}
          onChange={handleCodeChange}
          onMount={(editor, monaco) => {
            editorRef.current = editor;
            monacoRef.current = monaco;
            setMonacoReady(true);
          }}
          options={{
            minimap: { enabled: false },
            fontSize: 14,
            lineNumbers: "on",
            scrollBeyondLastLine: false,
            padding: { top: 12 },
          }}
        />
      </div>
    </div>
  );
}
