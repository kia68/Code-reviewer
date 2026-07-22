import { describe, it, expect, vi, beforeEach } from "vitest";
import "@testing-library/jest-dom/vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CodeEditor } from "./CodeEditor";
vi.mock("@monaco-editor/react", () => {
  let storedOnChange: ((value: string) => void) | null = null;

  return {
    __esModule: true,
    default: ({
      value,
      onChange,
    }: {
      value: string;
      onChange?: (value: string) => void;
    }) => {
      storedOnChange = onChange ?? null;
      return (
        <textarea
          data-testid="mock-editor"
          value={value}
          onChange={(e) => storedOnChange?.(e.target.value)}
          aria-label="Code editor"
        />
      );
    },
  };
});

describe("CodeEditor", () => {
  const defaultProps = {
    onSubmit: vi.fn(),
    isUploading: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the editor with a filename input and analyze button", () => {
    render(<CodeEditor {...defaultProps} />);

    expect(screen.getByLabelText("Filename")).toBeInTheDocument();
    expect(screen.getByText("Analyze Code")).toBeInTheDocument();
    expect(screen.getByTestId("mock-editor")).toBeInTheDocument();
  });

  it("defaults filename to Untitled.java", () => {
    render(<CodeEditor {...defaultProps} />);
    expect(screen.getByLabelText("Filename")).toHaveValue("Untitled.java");
  });

  it("disables analyze button when code is empty", () => {
    render(<CodeEditor {...defaultProps} />);
    expect(screen.getByText("Analyze Code")).toBeDisabled();
  });

  it("enables analyze button when code is entered", async () => {
    render(<CodeEditor {...defaultProps} />);

    const editor = screen.getByTestId("mock-editor");
    fireEvent.change(editor, { target: { value: "class Foo {}" } });

    expect(screen.getByText("Analyze Code")).not.toBeDisabled();
  });

  it("calls onSubmit with no args on analyze click", async () => {
    const onSubmit = vi.fn();
    render(<CodeEditor onSubmit={onSubmit} isUploading={false} />);

    fireEvent.change(screen.getByTestId("mock-editor"), { target: { value: "class Foo {}" } });
    fireEvent.click(screen.getByText("Analyze Code"));

    expect(onSubmit).toHaveBeenCalledOnce();
    expect(onSubmit).toHaveBeenCalledWith();
  });

  it("shows filename label when fileName prop is provided", () => {
    render(
      <CodeEditor
        onSubmit={defaultProps.onSubmit}
        isUploading={false}
        fileName="Main.java"
        fileContent=""
      />,
    );

    expect(screen.getByText("Main.java")).toBeInTheDocument();
    expect(screen.queryByLabelText("Filename")).not.toBeInTheDocument();
  });

  it("shows Save as new version button when isEditing", () => {
    const onSave = vi.fn();
    render(
      <CodeEditor
        onSubmit={defaultProps.onSubmit}
        isUploading={false}
        fileName="Main.java"
        fileContent=""
        isEditing
        onSave={onSave}
      />,
    );

    expect(screen.getByText("Save as new version")).toBeInTheDocument();
    expect(screen.queryByText("Analyze Code")).not.toBeInTheDocument();
  });

  it("shows Analyzing... and disables button while uploading", () => {
    render(<CodeEditor onSubmit={defaultProps.onSubmit} isUploading={true} />);
    expect(screen.getByText("Analyzing...")).toBeDisabled();
  });
});
